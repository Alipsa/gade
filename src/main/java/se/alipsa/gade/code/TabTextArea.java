package se.alipsa.gade.code;

import java.io.File;

public interface TabTextArea {

  /**
   * @return the file this textarea is related to
   * or null if no such relation exists
   */
  File getFile();

  /**
   * set the file this textarea is related to
   *
   * @param file the file to associate with
   */
  void setFile(File file);

  /**
   * @return the textual content (String) of this text area
   * which resides in a tab.
   */
  String getTextContent();

  /**
   *
   * @return the entire text from the TabTextArea
   */
  String getAllTextContent();

  /**
   * replace the entire text of the TabTextArea
   */
  void replaceContentText(String content, boolean isReadFromFile);

  /**
   * replace a section of text of the TabTextArea
   */
  void replaceContentText(int start, int end, String content);
}
