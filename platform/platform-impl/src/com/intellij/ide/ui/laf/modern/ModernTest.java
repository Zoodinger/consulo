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
package com.intellij.ide.ui.laf.modern;

import com.intellij.ide.ui.laf.modernDark.ModernDarkLaf;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.ColoredListCellRendererWrapper;
import com.intellij.ui.ShowUIDefaultsAction;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.components.JBCheckBox;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Konstantin Bulenkov
 */
public class ModernTest {
  private JBCheckBox myJBCheckBox1;
  private JBCheckBox myJBCheckBox2;
  private JBCheckBox myJBCheckBox3;
  private JBCheckBox myJBCheckBox4;
  private JBCheckBox myJBCheckBox5;
  private JComboBox myComboBox1;
  private JComboBox myComboBox2;
  private JComboBox myComboBox3;
  private JComboBox myComboBox4;
  private JComboBox myComboBox5;
  private JTextField myTextField1;
  private JTextField myThisTextIsDisabledTextField;
  private JPasswordField myPasswordField1;
  private JPanel myRoot;
  private JButton myHelpButton;
  private JButton myCancelButton;
  private JButton myDisabledButton;
  private JButton myDefaultButton;
  private JTextField myTextField2;
  private JTextField myTextField3;
  private JTextField myTextField4;
  private JSpinner mySpinner1;
  private JProgressBar myProgressBar1;
  private JButton myProgressButton;
  private JProgressBar myProgressBar2;
  private JButton myStartButton;
  private JButton myDisabledDefaultButton;
  private JList myList1;
  private JList myList2;

  public ModernTest() {
    myProgressButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        if (myProgressButton.getText().equals("Start")) {
          myProgressBar1.setIndeterminate(true);
          myProgressButton.setText("Stop");
        }
        else {
          myProgressBar1.setIndeterminate(false);
          myProgressButton.setText("Start");
        }
      }
    });
    myComboBox1.setRenderer(new ColoredListCellRendererWrapper<Object>() {
      @Override
      protected void doCustomize(JList list, Object value, int index, boolean selected, boolean hasFocus) {
        append(value.toString());
      }
    });
    myStartButton.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        myStartButton.setEnabled(false);
        new Thread() {
          @Override
          public void run() {
            while (myProgressBar2.getValue() < 100) {
              try {
                sleep(20);
              }
              catch (InterruptedException e1) {
              }
              myProgressBar2.setValue(myProgressBar2.getValue() + 1);
            }

            try {
              sleep(1000);
            }
            catch (InterruptedException e1) {
            }

            myProgressBar2.setValue(0);
            myStartButton.setEnabled(true);
          }
        }.start();
      }
    });

    List<String> items = new ArrayList<String>();
    for(int i = 0; i < 100; i++) {
      items.add("Item" + i);
    }
    myList1.setModel(new CollectionListModel<String>(items));
    myList2.setModel(new CollectionListModel<String>(items));

    myList2.setCellRenderer(new ColoredListCellRendererWrapper<String>() {
      @Override
      protected void doCustomize(JList list, String value, int index, boolean selected, boolean hasFocus) {
        append(value, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES);
      }
    });
  }

  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(new ModernDarkLaf());
    }
    catch (UnsupportedLookAndFeelException ignored) {
    }
    final JFrame frame = new JFrame("Modern White Demo");
    frame.setSize(900, 500);
    final ModernTest form = new ModernTest();
    final JPanel root = form.myRoot;
    frame.setContentPane(root);
    frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
    Toolkit.getDefaultToolkit().addAWTEventListener(new AWTEventListener() {
      @Override
      public void eventDispatched(AWTEvent event) {
        if (event instanceof KeyEvent && event.getID() == KeyEvent.KEY_PRESSED && ((KeyEvent)event).getKeyCode() == KeyEvent.VK_F1) {
          new ShowUIDefaultsAction().actionPerformed(null);
        }
      }
    }, AWTEvent.KEY_EVENT_MASK);
    SwingUtilities.invokeLater(new Runnable() {
      @Override
      public void run() {
        frame.setVisible(true);
      }
    });
  }

  private void createUIComponents() {
    myDisabledDefaultButton = new JButton(){
      @Override
      public boolean isDefaultButton() {
        return true;
      }
    };
    myDefaultButton = new JButton(){
      @Override
      public boolean isDefaultButton() {
        return true;
      }
    };
  }
}
