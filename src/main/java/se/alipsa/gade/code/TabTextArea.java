package se.alipsa.gade.code;

import java.io.File;

public interface TabTextArea {

  File getFile();

  void setFile(File file);

  String getTextContent();

  /**
   *
   * @return the entire text from the TabTextArea
   */
  String getAllTextContent();

  void replaceContentText(String content, boolean isReadFromFile);

  void replaceContentText(int start, int end, String content);
}
