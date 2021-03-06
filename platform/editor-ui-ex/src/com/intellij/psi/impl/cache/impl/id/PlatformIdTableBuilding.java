/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.ide.highlighter.HighlighterFactory;
import com.intellij.lang.Language;
import com.intellij.lang.LanguageParserDefinitions;
import com.intellij.lang.LanguageVersion;
import com.intellij.lang.ParserDefinition;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.ex.util.LexerEditorHighlighter;
import com.intellij.openapi.editor.highlighter.EditorHighlighter;
import com.intellij.openapi.editor.highlighter.HighlighterIterator;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.fileTypes.impl.CustomSyntaxTableFileType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.CustomHighlighterTokenType;
import com.intellij.psi.impl.cache.impl.BaseFilterLexer;
import com.intellij.psi.impl.cache.impl.IndexPatternUtil;
import com.intellij.psi.impl.cache.impl.OccurrenceConsumer;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexEntry;
import com.intellij.psi.impl.cache.impl.todo.TodoIndexers;
import com.intellij.psi.search.IndexPattern;
import com.intellij.psi.tree.IElementType;
import com.intellij.psi.tree.TokenSet;
import com.intellij.util.LanguageVersionUtil;
import com.intellij.util.codeInsight.CommentUtilCore;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.indexing.DataIndexer;
import com.intellij.util.indexing.FileContent;
import com.intellij.util.indexing.SubstitutedFileType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Author: dmitrylomov
 */
public abstract class PlatformIdTableBuilding {
  public static final Key<EditorHighlighter> EDITOR_HIGHLIGHTER = new Key<EditorHighlighter>("Editor");
  private static final Map<FileType, DataIndexer<TodoIndexEntry, Integer, FileContent>> ourTodoIndexers =
          new HashMap<FileType, DataIndexer<TodoIndexEntry, Integer, FileContent>>();
  private static final TokenSet ABSTRACT_FILE_COMMENT_TOKENS =
          TokenSet.create(CustomHighlighterTokenType.LINE_COMMENT, CustomHighlighterTokenType.MULTI_LINE_COMMENT);

  private PlatformIdTableBuilding() {
  }

  @Nullable
  public static DataIndexer<TodoIndexEntry, Integer, FileContent> getTodoIndexer(FileType fileType, Project project, final VirtualFile virtualFile) {
    final DataIndexer<TodoIndexEntry, Integer, FileContent> indexer = ourTodoIndexers.get(fileType);

    if (indexer != null) {
      return indexer;
    }

    final DataIndexer<TodoIndexEntry, Integer, FileContent> extIndexer;
    if (fileType instanceof SubstitutedFileType && !((SubstitutedFileType)fileType).isSameFileType()) {
      SubstitutedFileType sft = (SubstitutedFileType)fileType;
      extIndexer = new CompositeTodoIndexer(getTodoIndexer(sft.getOriginalFileType(), project, virtualFile),
                                            getTodoIndexer(sft.getFileType(), project, virtualFile));
    }
    else {
      extIndexer = TodoIndexers.INSTANCE.forFileType(fileType);
    }
    if (extIndexer != null) {
      return extIndexer;
    }

    if (fileType instanceof LanguageFileType) {
      final Language lang = ((LanguageFileType)fileType).getLanguage();
      final ParserDefinition parserDef = LanguageParserDefinitions.INSTANCE.forLanguage(lang);
      LanguageVersion<Language> languageVersion = LanguageVersionUtil.findLanguageVersion(lang, project, virtualFile);
      final TokenSet commentTokens = parserDef != null ? parserDef.getCommentTokens(languageVersion) : null;
      if (commentTokens != null) {
        return new TokenSetTodoIndexer(commentTokens, languageVersion, virtualFile, project);
      }
    }

    if (fileType instanceof CustomSyntaxTableFileType) {
      return new TokenSetTodoIndexer(ABSTRACT_FILE_COMMENT_TOKENS, null, virtualFile, project);
    }

    return null;
  }

  public static boolean checkCanUseCachedEditorHighlighter(final CharSequence chars, final EditorHighlighter editorHighlighter) {
    assert editorHighlighter instanceof LexerEditorHighlighter;
    final boolean b = ((LexerEditorHighlighter)editorHighlighter).checkContentIsEqualTo(chars);
    if (!b) {
      final Logger logger = Logger.getInstance(IdTableBuilding.class.getName());
      logger.warn("Unexpected mismatch of editor highlighter content with indexing content");
    }
    return b;
  }

  @Deprecated
  public static void registerTodoIndexer(@NotNull FileType fileType, DataIndexer<TodoIndexEntry, Integer, FileContent> indexer) {
    ourTodoIndexers.put(fileType, indexer);
  }

  public static boolean isTodoIndexerRegistered(@NotNull FileType fileType) {
    return ourTodoIndexers.containsKey(fileType) || TodoIndexers.INSTANCE.forFileType(fileType) != null;
  }

  private static class CompositeTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {
    private final DataIndexer<TodoIndexEntry, Integer, FileContent>[] indexers;

    public CompositeTodoIndexer(@NotNull DataIndexer<TodoIndexEntry, Integer, FileContent>... indexers) {
      this.indexers = indexers;
    }

    @NotNull
    @Override
    public Map<TodoIndexEntry, Integer> map(FileContent inputData) {
      Map<TodoIndexEntry, Integer> result = ContainerUtil.newTroveMap();
      for (DataIndexer<TodoIndexEntry, Integer, FileContent> indexer : indexers) {
        for (Map.Entry<TodoIndexEntry, Integer> entry : indexer.map(inputData).entrySet()) {
          TodoIndexEntry key = entry.getKey();
          if (result.containsKey(key)) {
            result.put(key, result.get(key) + entry.getValue());
          }
          else {
            result.put(key, entry.getValue());
          }
        }
      }
      return result;
    }
  }

  private static class TokenSetTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {
    @NotNull
    private final TokenSet myCommentTokens;
    @Nullable
    private final LanguageVersion<Language> myLanguageVersion;
    private final VirtualFile myFile;
    private final Project myProject;

    public TokenSetTodoIndexer(@NotNull final TokenSet commentTokens,
                               @Nullable LanguageVersion<Language> languageVersion,
                               @NotNull final VirtualFile file,
                               @Nullable Project project) {
      myCommentTokens = commentTokens;
      myLanguageVersion = languageVersion;
      myFile = file;
      myProject = project;
    }

    @Override
    @NotNull
    public Map<TodoIndexEntry, Integer> map(final FileContent inputData) {
      if (IndexPatternUtil.getIndexPatternCount() > 0) {
        final CharSequence chars = inputData.getContentAsText();
        final OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(null, true);
        EditorHighlighter highlighter;

        final EditorHighlighter editorHighlighter = inputData.getUserData(EDITOR_HIGHLIGHTER);
        if (editorHighlighter != null && checkCanUseCachedEditorHighlighter(chars, editorHighlighter)) {
          highlighter = editorHighlighter;
        }
        else {
          highlighter = HighlighterFactory.createHighlighter(null, myFile);
          highlighter.setText(chars);
        }

        final int documentLength = chars.length();
        BaseFilterLexer.TodoScanningState todoScanningState = null;
        final HighlighterIterator iterator = highlighter.createIterator(0);

        Map<Language, LanguageVersion<?>> languageVersionCache = ContainerUtil.newLinkedHashMap();
        if(myLanguageVersion != null) {
          languageVersionCache.put(myLanguageVersion.getLanguage(), myLanguageVersion);
        }

        while (!iterator.atEnd()) {
          final IElementType token = iterator.getTokenType();

          if (myCommentTokens.contains(token) || isCommentToken(languageVersionCache, token)) {
            int start = iterator.getStart();
            if (start >= documentLength) break;
            int end = iterator.getEnd();

            todoScanningState =
                    BaseFilterLexer.advanceTodoItemsCount(chars.subSequence(start, Math.min(end, documentLength)), occurrenceConsumer, todoScanningState);
            if (end > documentLength) break;
          }
          iterator.advance();
        }
        final Map<TodoIndexEntry, Integer> map = new HashMap<TodoIndexEntry, Integer>();
        for (IndexPattern pattern : IndexPatternUtil.getIndexPatterns()) {
          final int count = occurrenceConsumer.getOccurrenceCount(pattern);
          if (count > 0) {
            map.put(new TodoIndexEntry(pattern.getPatternString(), pattern.isCaseSensitive()), count);
          }
        }
        return map;
      }
      return Collections.emptyMap();
    }

    private boolean isCommentToken(Map<Language, LanguageVersion<?>> cache, IElementType token) {
      Language language = token.getLanguage();
      LanguageVersion<?> languageVersion = cache.get(language);
      if(languageVersion == null) {
        cache.put(language, languageVersion = LanguageVersionUtil.findLanguageVersion(language, myProject, myFile));
      }
      return CommentUtilCore.isCommentToken(token, languageVersion);
    }
  }

  public static class PlainTextTodoIndexer implements DataIndexer<TodoIndexEntry, Integer, FileContent> {
    @Override
    @NotNull
    public Map<TodoIndexEntry, Integer> map(final FileContent inputData) {
      String chars = inputData.getContentAsText().toString(); // matching strings is faster than HeapCharBuffer

      final IndexPattern[] indexPatterns = IndexPatternUtil.getIndexPatterns();
      if (indexPatterns.length <= 0) {
        return Collections.emptyMap();
      }
      OccurrenceConsumer occurrenceConsumer = new OccurrenceConsumer(null, true);
      for (IndexPattern indexPattern : indexPatterns) {
        Pattern pattern = indexPattern.getPattern();
        if (pattern != null) {
          Matcher matcher = pattern.matcher(chars);
          while (matcher.find()) {
            if (matcher.start() != matcher.end()) {
              occurrenceConsumer.incTodoOccurrence(indexPattern);
            }
          }
        }
      }
      Map<TodoIndexEntry, Integer> map = new HashMap<TodoIndexEntry, Integer>();
      for (IndexPattern indexPattern : indexPatterns) {
        final int count = occurrenceConsumer.getOccurrenceCount(indexPattern);
        if (count > 0) {
          map.put(new TodoIndexEntry(indexPattern.getPatternString(), indexPattern.isCaseSensitive()), count);
        }
      }
      return map;
    }

  }

  static {
    IdTableBuilding.registerIdIndexer(PlainTextFileType.INSTANCE, new IdTableBuilding.PlainTextIndexer());
    registerTodoIndexer(PlainTextFileType.INSTANCE, new PlainTextTodoIndexer());

    //IdTableBuilding.registerIdIndexer(StdFileTypes.IDEA_MODULE, null);
    //IdTableBuilding.registerIdIndexer(StdFileTypes.IDEA_WORKSPACE, null);
    //IdTableBuilding.registerIdIndexer(StdFileTypes.IDEA_PROJECT, null);

    //registerTodoIndexer(StdFileTypes.IDEA_MODULE, null);
    //registerTodoIndexer(StdFileTypes.IDEA_WORKSPACE, null);
    //registerTodoIndexer(StdFileTypes.IDEA_PROJECT, null);
  }
}
