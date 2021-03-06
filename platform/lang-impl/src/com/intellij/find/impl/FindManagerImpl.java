/*
 * Copyright 2000-2015 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.find.impl;

import com.intellij.codeInsight.highlighting.HighlightManager;
import com.intellij.codeInsight.highlighting.HighlightManagerImpl;
import com.intellij.codeInsight.hint.HintManager;
import com.intellij.codeInsight.hint.HintManagerImpl;
import com.intellij.codeInsight.hint.HintUtil;
import com.intellij.find.*;
import com.intellij.find.findUsages.FindUsagesManager;
import com.intellij.find.impl.livePreview.SearchResults;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.ParserDefinition;
import com.intellij.lexer.Lexer;
import com.intellij.navigation.NavigationItem;
import com.intellij.notification.impl.NotificationsConfigurationImpl;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.IdeActions;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.*;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.ex.FoldingModelEx;
import com.intellij.openapi.editor.markup.RangeHighlighter;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.TextEditor;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.patterns.StringPattern;
import com.intellij.psi.*;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.ui.LightweightHint;
import com.intellij.ui.ReplacePromptDialog;
import com.intellij.usages.ChunkExtractor;
import com.intellij.usages.UsageViewManager;
import com.intellij.usages.impl.SyntaxHighlighterOverEditorHighlighter;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Predicate;
import com.intellij.util.messages.MessageBus;
import com.intellij.util.text.CharArrayUtil;
import com.intellij.util.text.ImmutableCharSequence;
import com.intellij.util.text.StringSearcher;
import gnu.trove.THashSet;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class FindManagerImpl extends FindManager {
  private static final Logger LOG = Logger.getInstance("#com.intellij.find.impl.FindManagerImpl");

  private final FindUsagesManager myFindUsagesManager;
  private boolean isFindWasPerformed = false;
  private boolean isSelectNextOccurrenceWasPerformed = false;
  private Point myReplaceInFilePromptPos = new Point(-1, -1);
  private Point myReplaceInProjectPromptPos = new Point(-1, -1);
  private final FindModel myFindInProjectModel = new FindModel();
  private final FindModel myFindInFileModel = new FindModel();
  private FindModel myFindNextModel = null;
  private FindModel myPreviousFindModel = null;
  private static final FindResultImpl NOT_FOUND_RESULT = new FindResultImpl();
  private final Project myProject;
  private final MessageBus myBus;
  private static final Key<Boolean> HIGHLIGHTER_WAS_NOT_FOUND_KEY = Key.create("com.intellij.find.impl.FindManagerImpl.HighlighterNotFoundKey");

  private FindDialog myFindDialog;

  public FindManagerImpl(Project project, FindSettings findSettings, UsageViewManager anotherManager, MessageBus bus) {
    myProject = project;
    myBus = bus;
    findSettings.initModelBySetings(myFindInProjectModel);

    myFindInFileModel.setCaseSensitive(findSettings.isLocalCaseSensitive());
    myFindInFileModel.setWholeWordsOnly(findSettings.isLocalWholeWordsOnly());
    myFindInFileModel.setRegularExpressions(findSettings.isLocalRegularExpressions());

    myFindUsagesManager = new FindUsagesManager(myProject, anotherManager);
    myFindInProjectModel.setMultipleFiles(true);

    NotificationsConfigurationImpl.remove("FindInPath");
  }

  @Override
  public FindModel createReplaceInFileModel() {
    FindModel model = new FindModel();
    model.copyFrom(getFindInFileModel());
    model.setReplaceState(true);
    model.setPromptOnReplace(false);
    return model;
  }

  @Override
  public int showPromptDialog(@NotNull final FindModel model, String title) {
    return showPromptDialogImpl(model, title, null);
  }

  @PromptResultValue
  public int showPromptDialogImpl(@NotNull final FindModel model, String title, @Nullable final MalformedReplacementStringException exception) {
    ReplacePromptDialog replacePromptDialog = new ReplacePromptDialog(model.isMultipleFiles(), title, myProject, exception) {
      @Override
      @Nullable
      public Point getInitialLocation() {
        if (model.isMultipleFiles() && myReplaceInProjectPromptPos.x >= 0 && myReplaceInProjectPromptPos.y >= 0) {
          return myReplaceInProjectPromptPos;
        }
        if (!model.isMultipleFiles() && myReplaceInFilePromptPos.x >= 0 && myReplaceInFilePromptPos.y >= 0) {
          return myReplaceInFilePromptPos;
        }
        return null;
      }
    };

    replacePromptDialog.show();

    if (model.isMultipleFiles()) {
      myReplaceInProjectPromptPos = replacePromptDialog.getLocation();
    }
    else {
      myReplaceInFilePromptPos = replacePromptDialog.getLocation();
    }
    return replacePromptDialog.getExitCode();
  }

  @Override
  public void showFindDialog(@NotNull final FindModel model, @NotNull final Runnable okHandler) {
    final Consumer<FindModel> handler = new Consumer<FindModel>() {
      @Override
      public void consume(FindModel findModel) {
        String stringToFind = findModel.getStringToFind();
        if (!StringUtil.isEmpty(stringToFind)) {
          FindSettings.getInstance().addStringToFind(stringToFind);
        }
        if (!findModel.isMultipleFiles()) {
          setFindWasPerformed();
        }
        if (findModel.isReplaceState()) {
          FindSettings.getInstance().addStringToReplace(findModel.getStringToReplace());
        }
        if (findModel.isMultipleFiles() && !findModel.isProjectScope() && findModel.getDirectoryName() != null) {
          FindSettings.getInstance().addDirectory(findModel.getDirectoryName());
          myFindInProjectModel.setWithSubdirectories(findModel.isWithSubdirectories());
        }
        okHandler.run();
      }
    };
    if (myFindDialog == null || Disposer.isDisposed(myFindDialog.getDisposable())) {
      myFindDialog = new FindDialog(myProject, model, handler) {
        @Override
        protected void dispose() {
          super.dispose();
          myFindDialog = null; // avoid strong ref!
        }
      };
      myFindDialog.setModal(true);
    }
    else if (myFindDialog.getModel().isReplaceState() != model.isReplaceState()) {
      myFindDialog.setModel(model);
      myFindDialog.setOkHandler(handler);
      return;
    }
    myFindDialog.show();
  }

  @Override
  @NotNull
  public FindModel getFindInFileModel() {
    return myFindInFileModel;
  }

  @Override
  @NotNull
  public FindModel getFindInProjectModel() {
    myFindInProjectModel.setFromCursor(false);
    myFindInProjectModel.setForward(true);
    myFindInProjectModel.setGlobal(true);
    return myFindInProjectModel;
  }

  @Override
  public boolean findWasPerformed() {
    return isFindWasPerformed;
  }

  @Override
  public void setFindWasPerformed() {
    isFindWasPerformed = true;
    isSelectNextOccurrenceWasPerformed = false;
  }

  @Override
  public boolean selectNextOccurrenceWasPerformed() {
    return isSelectNextOccurrenceWasPerformed;
  }

  @Override
  public void setSelectNextOccurrenceWasPerformed() {
    isSelectNextOccurrenceWasPerformed = true;
    isFindWasPerformed = false;
  }

  @Override
  public FindModel getFindNextModel() {
    return myFindNextModel;
  }

  @Override
  public FindModel getFindNextModel(@NotNull final Editor editor) {
    if (myFindNextModel == null) return null;

    EditorSearchSession search = EditorSearchSession.get(editor);
    if (search != null && !isSelectNextOccurrenceWasPerformed) {
      String textInField = search.getTextInField();
      if (!Comparing.equal(textInField, myFindInFileModel.getStringToFind()) && !textInField.isEmpty()) {
        FindModel patched = new FindModel();
        patched.copyFrom(myFindNextModel);
        patched.setStringToFind(textInField);
        return patched;
      }
    }

    return myFindNextModel;
  }

  @Override
  public void setFindNextModel(FindModel findNextModel) {
    myFindNextModel = findNextModel;
    myBus.syncPublisher(FIND_MODEL_TOPIC).findNextModelChanged();
  }

  @Override
  @NotNull
  public FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model) {
    return findString(text, offset, model, null);
  }

  @NotNull
  @Override
  public FindResult findString(@NotNull CharSequence text, int offset, @NotNull FindModel model, @Nullable VirtualFile file) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("offset=" + offset);
      LOG.debug("textlength=" + text.length());
      LOG.debug(model.toString());
    }

    return findStringLoop(text, offset, model, file, getFindContextPredicate(model, file, text));
  }

  private FindResult findStringLoop(CharSequence text, int offset, FindModel model, VirtualFile file, @Nullable Predicate<FindResult> filter) {
    final char[] textArray = CharArrayUtil.fromSequenceWithoutCopying(text);
    while (true) {
      FindResult result = doFindString(text, textArray, offset, model, file);
      if (filter == null || filter.apply(result)) {
        if (!model.isWholeWordsOnly()) {
          return result;
        }
        if (!result.isStringFound()) {
          return result;
        }
        if (isWholeWord(text, result.getStartOffset(), result.getEndOffset())) {
          return result;
        }
      }

      offset = model.isForward() ? result.getStartOffset() + 1 : result.getEndOffset() - 1;
      if (offset > text.length() || offset < 0) return NOT_FOUND_RESULT;
    }
  }

  private class FindExceptCommentsOrLiteralsData implements Predicate<FindResult> {
    private final VirtualFile myFile;
    private final FindModel myFindModel;
    private final TreeMap<Integer, Integer> mySkipRangesSet;
    private final CharSequence myText;

    private FindExceptCommentsOrLiteralsData(VirtualFile file, FindModel model, CharSequence text) {
      myFile = file;
      myFindModel = model.clone();
      myText = ImmutableCharSequence.asImmutable(text);

      TreeMap<Integer, Integer> result = new TreeMap<Integer, Integer>();

      if (model.isExceptComments() || model.isExceptCommentsAndStringLiterals()) {
        addRanges(file, model, text, result, FindModel.SearchContext.IN_COMMENTS);
      }

      if (model.isExceptStringLiterals() || model.isExceptCommentsAndStringLiterals()) {
        addRanges(file, model, text, result, FindModel.SearchContext.IN_STRING_LITERALS);
      }

      mySkipRangesSet = result;
    }

    private void addRanges(VirtualFile file, FindModel model, CharSequence text, TreeMap<Integer, Integer> result, FindModel.SearchContext searchContext) {
      FindModel clonedModel = model.clone();
      clonedModel.setSearchContext(searchContext);
      clonedModel.setForward(true);
      int offset = 0;

      while (true) {
        FindResult customResult = findStringLoop(text, offset, clonedModel, file, null);
        if (!customResult.isStringFound()) break;
        result.put(customResult.getStartOffset(), customResult.getEndOffset());
        offset = Math.max(customResult.getEndOffset(), offset + 1);  // avoid loop for zero size reg exps matches
        if (offset >= text.length()) break;
      }
    }

    boolean isAcceptableFor(FindModel model, VirtualFile file, CharSequence text) {
      return Comparing.equal(myFile, file) &&
             myFindModel.equals(model) &&
             myText.length() == text.length();
    }

    @Override
    public boolean apply(@Nullable FindResult input) {
      if (input == null || !input.isStringFound()) return true;
      NavigableMap<Integer, Integer> map = mySkipRangesSet.headMap(input.getStartOffset(), true);
      for (Map.Entry<Integer, Integer> e : map.descendingMap().entrySet()) {
        // [e.key, e.value] intersect with [input.start, input.end]
        if (e.getKey() <= input.getStartOffset() && (input.getStartOffset() <= e.getValue() || e.getValue() >= input.getEndOffset())) return false;
        if (e.getValue() <= input.getStartOffset()) break;
      }
      return true;
    }
  }

  private static Key<FindExceptCommentsOrLiteralsData> ourExceptCommentsOrLiteralsDataKey = Key.create("except.comments.literals.search.data");

  private Predicate<FindResult> getFindContextPredicate(@NotNull FindModel model, VirtualFile file, CharSequence text) {
    if (file == null) return null;
    FindModel.SearchContext context = model.getSearchContext();
    if (context == FindModel.SearchContext.ANY || context == FindModel.SearchContext.IN_COMMENTS ||
        context == FindModel.SearchContext.IN_STRING_LITERALS) {
      return null;
    }

    FindExceptCommentsOrLiteralsData data = model.getUserData(ourExceptCommentsOrLiteralsDataKey);
    if (data == null || !data.isAcceptableFor(model, file, text)) {
      model.putUserData(ourExceptCommentsOrLiteralsDataKey, data = new FindExceptCommentsOrLiteralsData(file, model, text));
    }

    return data;
  }

  @Override
  public int showMalformedReplacementPrompt(@NotNull FindModel model, String title, MalformedReplacementStringException exception) {
    return showPromptDialogImpl(model, title, exception);
  }

  @Override
  public FindModel getPreviousFindModel() {
    return myPreviousFindModel;
  }

  @Override
  public void setPreviousFindModel(FindModel previousFindModel) {
    myPreviousFindModel = previousFindModel;
  }

  private static boolean isWholeWord(CharSequence text, int startOffset, int endOffset) {
    boolean isWordStart;

    if (startOffset != 0) {
      boolean previousCharacterIsIdentifier =
              Character.isJavaIdentifierPart(text.charAt(startOffset - 1)) && (startOffset <= 1 || text.charAt(startOffset - 2) != '\\');
      boolean previousCharacterIsSameAsNext = text.charAt(startOffset - 1) == text.charAt(startOffset);

      boolean firstCharacterIsIdentifier = Character.isJavaIdentifierPart(text.charAt(startOffset));
      isWordStart = !firstCharacterIsIdentifier && !previousCharacterIsSameAsNext || firstCharacterIsIdentifier && !previousCharacterIsIdentifier;
    }
    else {
      isWordStart = true;
    }

    boolean isWordEnd;

    if (endOffset != text.length()) {
      boolean nextCharacterIsIdentifier = Character.isJavaIdentifierPart(text.charAt(endOffset));
      boolean nextCharacterIsSameAsPrevious = endOffset > 0 && text.charAt(endOffset) == text.charAt(endOffset - 1);
      boolean lastSearchedCharacterIsIdentifier = endOffset > 0 && Character.isJavaIdentifierPart(text.charAt(endOffset - 1));

      isWordEnd = lastSearchedCharacterIsIdentifier && !nextCharacterIsIdentifier || !lastSearchedCharacterIsIdentifier && !nextCharacterIsSameAsPrevious;
    }
    else {
      isWordEnd = true;
    }

    return isWordStart && isWordEnd;
  }

  @NotNull
  private static FindModel normalizeIfMultilined(@NotNull FindModel findmodel) {
    if (findmodel.isMultiline()) {
      final FindModel model = new FindModel();
      model.copyFrom(findmodel);
      final String s = model.getStringToFind();
      String newStringToFind;

      if (findmodel.isRegularExpressions()) {
        newStringToFind = StringUtil.replace(s, "\n", "\\n\\s*"); // add \\s* for convenience
      }
      else {
        newStringToFind = StringUtil.escapeToRegexp(s);
        model.setRegularExpressions(true);
      }
      model.setStringToFind(newStringToFind);

      return model;
    }
    return findmodel;
  }

  @NotNull
  private FindResult doFindString(@NotNull CharSequence text,
                                  @Nullable char[] textArray,
                                  int offset,
                                  @NotNull FindModel findmodel,
                                  @Nullable VirtualFile file) {
    FindModel model = normalizeIfMultilined(findmodel);
    String toFind = model.getStringToFind();
    if (toFind.isEmpty()) {
      return NOT_FOUND_RESULT;
    }

    if (model.isInCommentsOnly() || model.isInStringLiteralsOnly()) {
      if (file == null) return NOT_FOUND_RESULT;
      return findInCommentsAndLiterals(text, textArray, offset, model, file);
    }

    if (model.isRegularExpressions()) {
      return findStringByRegularExpression(text, offset, model);
    }

    final StringSearcher searcher = createStringSearcher(model);

    int index;
    if (model.isForward()) {
      final int res = searcher.scan(text, textArray, offset, text.length());
      index = res < 0 ? -1 : res;
    }
    else {
      index = offset == 0 ? -1 : searcher.scan(text, textArray, 0, offset - 1);
    }
    if (index < 0) {
      return NOT_FOUND_RESULT;
    }
    return new FindResultImpl(index, index + toFind.length());
  }

  @NotNull
  private static StringSearcher createStringSearcher(@NotNull FindModel model) {
    return new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), model.isForward());
  }

  public static void clearPreviousFindData(FindModel model) {
    model.putUserData(ourCommentsLiteralsSearchDataKey, null);
    model.putUserData(ourExceptCommentsOrLiteralsDataKey, null);
  }

  private static class CommentsLiteralsSearchData {
    final VirtualFile lastFile;
    int startOffset = 0;
    final SyntaxHighlighterOverEditorHighlighter highlighter;

    TokenSet tokensOfInterest;
    final StringSearcher searcher;
    final Matcher matcher;
    final Set<Language> relevantLanguages;
    final FindModel model;

    public CommentsLiteralsSearchData(VirtualFile lastFile,
                                      Set<Language> relevantLanguages,
                                      SyntaxHighlighterOverEditorHighlighter highlighter,
                                      TokenSet tokensOfInterest,
                                      StringSearcher searcher,
                                      Matcher matcher,
                                      FindModel model) {
      this.lastFile = lastFile;
      this.highlighter = highlighter;
      this.tokensOfInterest = tokensOfInterest;
      this.searcher = searcher;
      this.matcher = matcher;
      this.relevantLanguages = relevantLanguages;
      this.model = model;
    }
  }

  private static final Key<CommentsLiteralsSearchData> ourCommentsLiteralsSearchDataKey = Key.create("comments.literals.search.data");

  @NotNull
  private FindResult findInCommentsAndLiterals(@NotNull CharSequence text,
                                               char[] textArray,
                                               int offset,
                                               @NotNull FindModel model,
                                               @NotNull final VirtualFile file) {
    FileType ftype = file.getFileType();
    Language lang = null;
    if (ftype instanceof LanguageFileType) {
      lang = ((LanguageFileType)ftype).getLanguage();
    }

    CommentsLiteralsSearchData data = model.getUserData(ourCommentsLiteralsSearchDataKey);
    if (data == null || !Comparing.equal(data.lastFile, file) || !data.model.equals(model)) {
      SyntaxHighlighter highlighter = getHighlighter(file, lang);

      if (highlighter == null) {
        // no syntax highlighter -> no search
        return NOT_FOUND_RESULT;
      }

      TokenSet tokensOfInterest = TokenSet.EMPTY;
      Set<Language> relevantLanguages;
      if (lang != null) {
        final Language finalLang = lang;
        relevantLanguages = ApplicationManager.getApplication().runReadAction(new Computable<Set<Language>>() {
          @Override
          public Set<Language> compute() {
            THashSet<Language> result = new THashSet<Language>();

            FileViewProvider viewProvider = PsiManager.getInstance(myProject).findViewProvider(file);
            if (viewProvider != null) {
              result.addAll(viewProvider.getLanguages());
            }

            if (result.isEmpty()) {
              result.add(finalLang);
            }
            return result;
          }
        });

        for (Language relevantLanguage : relevantLanguages) {
          tokensOfInterest = addTokenTypesForLanguage(model, relevantLanguage, tokensOfInterest);
        }

        if (model.isInStringLiteralsOnly()) {
          // TODO: xml does not have string literals defined so we add XmlAttributeValue element type as convenience
          final Lexer xmlLexer = getHighlighter(null, Language.findLanguageByID("XML")).getHighlightingLexer();
          final String marker = "xxx";
          xmlLexer.start("<a href=\"" + marker + "\" />");

          while (!marker.equals(xmlLexer.getTokenText())) {
            xmlLexer.advance();
            if (xmlLexer.getTokenType() == null) break;
          }

          IElementType convenienceXmlAttrType = xmlLexer.getTokenType();
          if (convenienceXmlAttrType != null) {
            tokensOfInterest = TokenSet.orSet(tokensOfInterest, TokenSet.create(convenienceXmlAttrType));
          }
        }
      }
      else {
        relevantLanguages = ContainerUtil.newHashSet();
        if (ftype instanceof AbstractFileType) {
          if (model.isInCommentsOnly()) {
            tokensOfInterest = TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);
          }
          if (model.isInStringLiteralsOnly()) {
            tokensOfInterest =
                    TokenSet.orSet(tokensOfInterest, TokenSet.create(CustomHighlighterTokenType.STRING, CustomHighlighterTokenType.SINGLE_QUOTED_STRING));
          }
        }
      }

      Matcher matcher = model.isRegularExpressions() ? compileRegExp(model, "") : null;
      StringSearcher searcher = matcher != null ? null : new StringSearcher(model.getStringToFind(), model.isCaseSensitive(), true);
      SyntaxHighlighterOverEditorHighlighter highlighterAdapter = new SyntaxHighlighterOverEditorHighlighter(highlighter, file, myProject);
      data = new CommentsLiteralsSearchData(file, relevantLanguages, highlighterAdapter, tokensOfInterest, searcher, matcher, model.clone());
      data.highlighter.restart(text);
      model.putUserData(ourCommentsLiteralsSearchDataKey, data);
    }

    int initialStartOffset = model.isForward() && data.startOffset < offset ? data.startOffset : 0;
    data.highlighter.resetPosition(initialStartOffset);
    final Lexer lexer = data.highlighter.getHighlightingLexer();

    IElementType tokenType;
    TokenSet tokens = data.tokensOfInterest;

    int lastGoodOffset = 0;
    boolean scanningForward = model.isForward();
    FindResultImpl prevFindResult = NOT_FOUND_RESULT;

    while ((tokenType = lexer.getTokenType()) != null) {
      if (lexer.getState() == 0) lastGoodOffset = lexer.getTokenStart();

      final TextAttributesKey[] keys = data.highlighter.getTokenHighlights(tokenType);

      if (tokens.contains(tokenType) ||
          (model.isInStringLiteralsOnly() && ChunkExtractor.isHighlightedAsString(keys)) ||
          (model.isInCommentsOnly() && ChunkExtractor.isHighlightedAsComment(keys))) {
        int start = lexer.getTokenStart();
        int end = lexer.getTokenEnd();
        if (model.isInStringLiteralsOnly()) { // skip literal quotes itself from matching
          char c = text.charAt(start);
          if (c == '"' || c == '\'') {
            while (start < end && c == text.charAt(start)) {
              ++start;
              if (c == text.charAt(end - 1) && start < end) --end;
            }
          }
        }

        while (true) {
          FindResultImpl findResult = null;

          if (data.searcher != null) {
            int matchStart = data.searcher.scan(text, textArray, start, end);

            if (matchStart != -1 && matchStart >= start) {
              final int matchEnd = matchStart + model.getStringToFind().length();
              if (matchStart >= offset || !scanningForward) {
                findResult = new FindResultImpl(matchStart, matchEnd);
              }
              else {
                start = matchEnd;
                continue;
              }
            }
          }
          else {
            data.matcher.reset(StringPattern.newBombedCharSequence(text.subSequence(start, end)));
            if (data.matcher.find()) {
              final int matchEnd = start + data.matcher.end();
              int matchStart = start + data.matcher.start();
              if (matchStart >= offset || !scanningForward) {
                findResult = new FindResultImpl(matchStart, matchEnd);
              }
              else {
                start = matchEnd;
                continue;
              }
            }
          }

          if (findResult != null) {
            if (scanningForward) {
              data.startOffset = lastGoodOffset;
              return findResult;
            }
            else {

              if (findResult.getEndOffset() >= offset) return prevFindResult;
              prevFindResult = findResult;
              start = findResult.getEndOffset();
              continue;
            }
          }
          break;
        }
      }
      else {
        Language tokenLang = tokenType.getLanguage();
        if (tokenLang != lang && tokenLang != Language.ANY && !data.relevantLanguages.contains(tokenLang)) {
          tokens = addTokenTypesForLanguage(model, tokenLang, tokens);
          data.tokensOfInterest = tokens;
          data.relevantLanguages.add(tokenLang);
        }
      }

      lexer.advance();
    }

    return prevFindResult;
  }

  private static TokenSet addTokenTypesForLanguage(FindModel model, Language lang, TokenSet tokensOfInterest) {
    ParserDefinition definition = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
    if (definition != null) {
      for (LanguageVersion languageVersion : lang.getVersions()) {
        tokensOfInterest = TokenSet.orSet(tokensOfInterest, model.isInCommentsOnly() ? definition.getCommentTokens(languageVersion) : TokenSet.EMPTY);
        tokensOfInterest =
                TokenSet.orSet(tokensOfInterest, model.isInStringLiteralsOnly() ? definition.getStringLiteralElements(languageVersion) : TokenSet.EMPTY);
      }
    }
    return tokensOfInterest;
  }

  private static
  @Nullable
  SyntaxHighlighter getHighlighter(VirtualFile file, @Nullable Language lang) {
    SyntaxHighlighter syntaxHighlighter = lang != null ? SyntaxHighlighterFactory.getSyntaxHighlighter(lang, null, file) : null;
    if (lang == null || syntaxHighlighter instanceof PlainSyntaxHighlighter) {
      syntaxHighlighter = SyntaxHighlighterFactory.getSyntaxHighlighter(file.getFileType(), null, file);
    }

    return syntaxHighlighter;
  }

  private static FindResult findStringByRegularExpression(CharSequence text, int startOffset, FindModel model) {
    Matcher matcher = compileRegExp(model, text);
    if (matcher == null) {
      return NOT_FOUND_RESULT;
    }
    if (model.isForward()) {
      if (matcher.find(startOffset)) {
        if (matcher.end() <= text.length()) {
          return new FindResultImpl(matcher.start(), matcher.end());
        }
      }
      return NOT_FOUND_RESULT;
    }
    else {
      int start = -1;
      int end = -1;
      while (matcher.find() && matcher.end() < startOffset) {
        start = matcher.start();
        end = matcher.end();
      }
      if (start < 0) {
        return NOT_FOUND_RESULT;
      }
      return new FindResultImpl(start, end);
    }
  }

  private static Matcher compileRegExp(FindModel model, CharSequence text) {
    Pattern pattern = model.compileRegExp();
    return pattern == null ? null : pattern.matcher(StringPattern.newBombedCharSequence(text));
  }

  @Override
  public String getStringToReplace(@NotNull String foundString, @NotNull FindModel model) throws MalformedReplacementStringException {
    String toReplace = model.getStringToReplace();
    if (model.isRegularExpressions()) {
      return getStringToReplaceByRegexp0(foundString, model);
    }
    if (model.isPreserveCase()) {
      return replaceWithCaseRespect(toReplace, foundString);
    }
    return toReplace;
  }

  @Override
  public String getStringToReplace(@NotNull String foundString, @NotNull FindModel model, int startOffset, @NotNull CharSequence documentText)
          throws MalformedReplacementStringException {
    String toReplace = model.getStringToReplace();
    if (model.isRegularExpressions()) {
      return getStringToReplaceByRegexp(model, documentText, startOffset);
    }
    if (model.isPreserveCase()) {
      return replaceWithCaseRespect(toReplace, foundString);
    }
    return toReplace;
  }

  private static String getStringToReplaceByRegexp(@NotNull final FindModel model, @NotNull CharSequence text, int startOffset)
          throws MalformedReplacementStringException {
    Matcher matcher = compileRegexAndFindFirst(model, text, startOffset);
    return getStringToReplaceByRegexp(model, matcher);
  }

  private static String getStringToReplaceByRegexp(@NotNull final FindModel model, Matcher matcher) throws MalformedReplacementStringException {
    if (matcher == null) return null;
    try {
      String toReplace = model.getStringToReplace();
      return new RegExReplacementBuilder(matcher).createReplacement(toReplace);
    }
    catch (Exception e) {
      throw createMalformedReplacementException(model, e);
    }
  }

  private static Matcher compileRegexAndFindFirst(FindModel model, CharSequence text, int startOffset) {
    Matcher matcher = compileRegExp(model, text);

    if (model.isForward()) {
      if (!matcher.find(startOffset)) {
        return null;
      }
      if (matcher.end() > text.length()) {
        return null;
      }
    }
    else {
      int start = -1;
      while (matcher.find() && matcher.end() < startOffset) {
        start = matcher.start();
      }
      if (start < 0) {
        return null;
      }
    }
    return matcher;
  }

  private static MalformedReplacementStringException createMalformedReplacementException(FindModel model, Exception e) {
    return new MalformedReplacementStringException(FindBundle.message("find.replace.invalid.replacement.string", model.getStringToReplace()), e);
  }

  private static String getStringToReplaceByRegexp0(String foundString, final FindModel model) throws MalformedReplacementStringException {
    String toFind = model.getStringToFind();
    String toReplace = model.getStringToReplace();
    Pattern pattern;
    try {
      int flags = Pattern.MULTILINE;
      if (!model.isCaseSensitive()) {
        flags |= Pattern.CASE_INSENSITIVE;
      }
      pattern = Pattern.compile(toFind, flags);
    }
    catch (PatternSyntaxException e) {
      return toReplace;
    }

    Matcher matcher = pattern.matcher(foundString);
    if (matcher.matches()) {
      try {
        return matcher.replaceAll(StringUtil.unescapeStringCharacters(toReplace));
      }
      catch (Exception e) {
        throw createMalformedReplacementException(model, e);
      }
    }
    else {
      // There are valid situations (for example, IDEADEV-2543 or positive lookbehind assertions)
      // where an expression which matches a string in context will not match the same string
      // separately).
      return toReplace;
    }
  }

  private static String replaceWithCaseRespect(String toReplace, String foundString) {
    if (foundString.isEmpty() || toReplace.isEmpty()) return toReplace;
    StringBuilder buffer = new StringBuilder();

    if (Character.isUpperCase(foundString.charAt(0))) {
      buffer.append(Character.toUpperCase(toReplace.charAt(0)));
    }
    else {
      buffer.append(Character.toLowerCase(toReplace.charAt(0)));
    }

    if (toReplace.length() == 1) return buffer.toString();

    if (foundString.length() == 1) {
      buffer.append(toReplace.substring(1));
      return buffer.toString();
    }

    boolean isReplacementLowercase = true;
    for (int i = 0; i < toReplace.length(); i++) {
      isReplacementLowercase = Character.isLowerCase(toReplace.charAt(i));
      if (!isReplacementLowercase) break;
    }

    boolean isTailUpper = true;
    boolean isTailLower = true;
    for (int i = 1; i < foundString.length(); i++) {
      isTailUpper &= Character.isUpperCase(foundString.charAt(i));
      isTailLower &= Character.isLowerCase(foundString.charAt(i));
      if (!isTailUpper && !isTailLower) break;
    }

    if (isTailUpper && isReplacementLowercase) {
      buffer.append(StringUtil.toUpperCase(toReplace.substring(1)));
    }
    else if (isTailLower && isReplacementLowercase) {
      buffer.append(toReplace.substring(1).toLowerCase());
    }
    else {
      buffer.append(toReplace.substring(1));
    }
    return buffer.toString();
  }

  @Override
  public boolean canFindUsages(@NotNull PsiElement element) {
    return element.isValid() && myFindUsagesManager.canFindUsages(element);
  }

  @Override
  public void findUsages(@NotNull PsiElement element) {
    findUsages(element, false);
  }

  @Override
  public void findUsagesInScope(@NotNull PsiElement element, @NotNull SearchScope searchScope) {
    myFindUsagesManager.findUsages(element, null, null, false, searchScope);
  }

  @Override
  public void findUsages(@NotNull PsiElement element, boolean showDialog) {
    myFindUsagesManager.findUsages(element, null, null, showDialog, null);
  }

  @Override
  public void showSettingsAndFindUsages(@NotNull NavigationItem[] targets) {
    FindUsagesManager.showSettingsAndFindUsages(targets);
  }

  @Override
  public void clearFindingNextUsageInFile() {
    myFindUsagesManager.clearFindingNextUsageInFile();
  }

  @Override
  public void findUsagesInEditor(@NotNull PsiElement element, @NotNull FileEditor fileEditor) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();
      Document document = editor.getDocument();
      PsiFile psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document);

      myFindUsagesManager.findUsages(element, psiFile, fileEditor, false, null);
    }
  }

  private static boolean tryToFindNextUsageViaEditorSearchComponent(Editor editor, SearchResults.Direction forwardOrBackward) {
    EditorSearchSession search = EditorSearchSession.get(editor);
    if (search != null && search.hasMatches()) {
      if (forwardOrBackward == SearchResults.Direction.UP) {
        search.searchBackward();
      }
      else {
        search.searchForward();
      }
      return true;
    }
    return false;
  }

  @Override
  public boolean findNextUsageInEditor(@NotNull FileEditor fileEditor) {
    return findNextUsageInFile(fileEditor, SearchResults.Direction.DOWN);
  }

  private boolean findNextUsageInFile(@NotNull FileEditor fileEditor, @NotNull SearchResults.Direction direction) {
    if (fileEditor instanceof TextEditor) {
      TextEditor textEditor = (TextEditor)fileEditor;
      Editor editor = textEditor.getEditor();
      editor.getCaretModel().removeSecondaryCarets();
      if (tryToFindNextUsageViaEditorSearchComponent(editor, direction)) {
        return true;
      }

      RangeHighlighter[] highlighters = ((HighlightManagerImpl)HighlightManager.getInstance(myProject)).getHighlighters(editor);
      if (highlighters.length > 0) {
        return highlightNextHighlighter(highlighters, editor, editor.getCaretModel().getOffset(), direction == SearchResults.Direction.DOWN, false);
      }
    }

    if (direction == SearchResults.Direction.DOWN) {
      return myFindUsagesManager.findNextUsageInFile(fileEditor);
    }
    return myFindUsagesManager.findPreviousUsageInFile(fileEditor);
  }

  @Override
  public boolean findPreviousUsageInEditor(@NotNull FileEditor fileEditor) {
    return findNextUsageInFile(fileEditor, SearchResults.Direction.UP);
  }

  private static boolean highlightNextHighlighter(RangeHighlighter[] highlighters, Editor editor, int offset, boolean isForward, boolean secondPass) {
    RangeHighlighter highlighterToSelect = null;
    Object wasNotFound = editor.getUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY);
    for (RangeHighlighter highlighter : highlighters) {
      int start = highlighter.getStartOffset();
      int end = highlighter.getEndOffset();
      if (highlighter.isValid() && start < end) {
        if (isForward && (start > offset || start == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getStartOffset() > start) highlighterToSelect = highlighter;
        }
        if (!isForward && (end < offset || end == offset && secondPass)) {
          if (highlighterToSelect == null || highlighterToSelect.getEndOffset() < end) highlighterToSelect = highlighter;
        }
      }
    }
    if (highlighterToSelect != null) {
      expandFoldRegionsIfNecessary(editor, highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getSelectionModel().setSelection(highlighterToSelect.getStartOffset(), highlighterToSelect.getEndOffset());
      editor.getCaretModel().moveToOffset(highlighterToSelect.getStartOffset());
      ScrollType scrollType;
      if (secondPass) {
        scrollType = isForward ? ScrollType.CENTER_UP : ScrollType.CENTER_DOWN;
      }
      else {
        scrollType = isForward ? ScrollType.CENTER_DOWN : ScrollType.CENTER_UP;
      }
      editor.getScrollingModel().scrollToCaret(scrollType);
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, null);
      return true;
    }

    if (wasNotFound == null) {
      editor.putUserData(HIGHLIGHTER_WAS_NOT_FOUND_KEY, Boolean.TRUE);
      String message = FindBundle.message("find.highlight.no.more.highlights.found");
      if (isForward) {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_NEXT);
        String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.isEmpty()) {
          message = FindBundle.message("find.search.again.from.top.action.message", message);
        }
        else {
          message = FindBundle.message("find.search.again.from.top.hotkey.message", message, shortcutsText);
        }
      }
      else {
        AnAction action = ActionManager.getInstance().getAction(IdeActions.ACTION_FIND_PREVIOUS);
        String shortcutsText = KeymapUtil.getFirstKeyboardShortcutText(action);
        if (shortcutsText.isEmpty()) {
          message = FindBundle.message("find.search.again.from.bottom.action.message", message);
        }
        else {
          message = FindBundle.message("find.search.again.from.bottom.hotkey.message", message, shortcutsText);
        }
      }
      JComponent component = HintUtil.createInformationLabel(message);
      final LightweightHint hint = new LightweightHint(component);
      HintManagerImpl.getInstanceImpl().showEditorHint(hint, editor, HintManager.UNDER, HintManager.HIDE_BY_ANY_KEY |
                                                                                        HintManager.HIDE_BY_TEXT_CHANGE | HintManager.HIDE_BY_SCROLLING, 0,
                                                       false);
      return true;
    }
    if (!secondPass) {
      offset = isForward ? 0 : editor.getDocument().getTextLength();
      return highlightNextHighlighter(highlighters, editor, offset, isForward, true);
    }

    return false;
  }

  private static void expandFoldRegionsIfNecessary(@NotNull Editor editor, final int startOffset, int endOffset) {
    final FoldingModel foldingModel = editor.getFoldingModel();
    final FoldRegion[] regions;
    if (foldingModel instanceof FoldingModelEx) {
      regions = ((FoldingModelEx)foldingModel).fetchTopLevel();
    }
    else {
      regions = foldingModel.getAllFoldRegions();
    }
    if (regions == null) {
      return;
    }
    int i = Arrays.binarySearch(regions, null, new Comparator<FoldRegion>() {
      @Override
      public int compare(FoldRegion o1, FoldRegion o2) {
        // Find the first region that ends after the given start offset
        if (o1 == null) {
          return startOffset - o2.getEndOffset();
        }
        else {
          return o1.getEndOffset() - startOffset;
        }
      }
    });
    if (i < 0) {
      i = -i - 1;
    }
    else {
      i++; // Don't expand fold region that ends at the start offset.
    }
    if (i >= regions.length) {
      return;
    }
    final List<FoldRegion> toExpand = new ArrayList<FoldRegion>();
    for (; i < regions.length; i++) {
      final FoldRegion region = regions[i];
      if (region.getStartOffset() >= endOffset) {
        break;
      }
      if (!region.isExpanded()) {
        toExpand.add(region);
      }
    }
    if (toExpand.isEmpty()) {
      return;
    }
    foldingModel.runBatchFoldingOperation(new Runnable() {
      @Override
      public void run() {
        for (FoldRegion region : toExpand) {
          region.setExpanded(true);
        }
      }
    });
  }

  @NotNull
  public FindUsagesManager getFindUsagesManager() {
    return myFindUsagesManager;
  }
}
