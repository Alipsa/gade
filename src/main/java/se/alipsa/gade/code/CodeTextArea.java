package se.alipsa.gade.code;

import static se.alipsa.gade.Constants.INDENT;

import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.IndexRange;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import se.alipsa.gade.Gade;
import se.alipsa.gade.UnStyledCodeArea;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.StringUtils;

import java.io.File;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Base class for all code areas
*/
public abstract class CodeTextArea extends UnStyledCodeArea implements TabTextArea {

  /** The file this texarea is editing */
  protected File file;

  /**
   * A flag that indicates whether a change of text should be considered
   * as changed text (true), e-g- an edit by the user,
   * or new text (false), e.g read from file
   */
  protected boolean blockChange = false;

  private TextAreaTab parentTab;

  private final Pattern whiteSpace = Pattern.compile( "^\\s+" );

  /**
   * Default ctor
   */
  public CodeTextArea() {

    getStyleClass().add("codeTextArea");
    setUseInitialStyleForInsertion(true);

    Platform.runLater(() -> setParagraphGraphicFactory(LineNumberFactory.get(this)));

    // recompute the syntax highlighting 400 ms after user stops editing area

    // plain changes = ignore style changes that are emitted when syntax highlighting is reapplied
    // multi plain changes = save computation by not rerunning the code multiple times
    //   when making multiple changes (e.g. renaming a method at multiple parts in file)
    multiPlainChanges()

        // do not emit an event until 400 ms have passed since the last emission of previous stream
        .successionEnds(Duration.ofMillis(400))

        // run the following code block when previous stream emits an event
        .subscribe(ignore -> highlightSyntax());

    /*
    Iterator<String> it = getStylesheets().iterator();
    System.out.println("Stylesheets for " + getClass().getSimpleName());
    for (String sheet : getStylesheets()) {
      System.out.println(sheet);
    }
        System.out.println("Style classes for " + getClass().getSimpleName());
    for (String styleClass : getStyleClass()) {
      System.out.println(styleClass);
    }
    */
  }

  /**
   * Constructor relating this text area to a TextAreaTab.
   *
   * @param parent the TextAreaTab that should be the parent of this code text area
   */
  public CodeTextArea(TextAreaTab parent) {
    this();
    this.parentTab = parent;

    plainTextChanges().subscribe(ptc -> {
      if (parentTab != null && !parentTab.isChanged() && !blockChange) {
        parentTab.contentChanged();
      }
    });

    addEventHandler(KeyEvent.KEY_PRESSED, e -> {
      KeyCode keyCode = e.getCode();
      if (e.isControlDown()) {
        if (KeyCode.F.equals(keyCode)) {
          Gade.instance().getMainMenu().displayFind();
        } else if (KeyCode.S.equals(keyCode)) {
          Gade.instance().getMainMenu().saveContent(parentTab);
        } else if (e.isShiftDown() && KeyCode.C.equals(keyCode)) {
          Gade.instance().getMainMenu().commentLines();
        } else if (e.isShiftDown() && (KeyCode.QUOTE.equals(e.getCode()) || KeyCode.ASTERISK.equals(e.getCode()))) {
          insertText(getCaretPosition(), "`");
        } else if (e.isAltDown() && KeyCode.QUOTE.equals(e.getCode())) {
          insertText(getCaretPosition(), "´");
        } else if (KeyCode.QUOTE.equals(e.getCode())) {
          insertText(getCaretPosition(), "^");
        }
      } else if (e.isShiftDown()) {
        if (KeyCode.TAB.equals(keyCode)) {
          String selected = selectedTextProperty().getValue();
          if ("".equals(selected)) {
            String line = getText(getCurrentParagraph());
            if (line.startsWith(INDENT)) {
              String s = line.substring(INDENT.length());
              int orgPos = getCaretPosition();
              moveTo(getCurrentParagraph(), 0);
              int start = getCaretPosition();
              int end = start + line.length();
              replaceText(start, end, s);
              moveTo(orgPos - INDENT.length());
            } //else {
              //NO tab in the beginning, nothing to do
            //}
          } else {
            IndexRange range = getSelection();
            int start = range.getStart();
            String s = backIndentText(selected);
            replaceText(range, s);
            selectRange(start, start + s.length());
          }
          e.consume();
        }
      } else if (KeyCode.ENTER.equals(keyCode)) {
        // Maintain indentation on the next line
        Matcher m = whiteSpace.matcher( getParagraph( getCurrentParagraph() ).getSegments().get( 0 ) );
        if ( m.find() ) {
          Platform.runLater( () -> insertText( getCaretPosition(), m.group() ) );
        }
      }
    });
    InputMap<KeyEvent> im = InputMap.consume(
        EventPattern.keyPressed(KeyCode.TAB),
        e -> {
          String selected = selectedTextProperty().getValue();
          if (!"".equals(selected)) {
            IndexRange range = getSelection();
            int start = range.getStart();
            String indented = indentText(selected);
            replaceSelection(indented);
            selectRange(start, start + indented.length());
          } else {
            String line = getText(getCurrentParagraph());
            int orgPos = getCaretPosition();
            moveTo(getCurrentParagraph(), 0);
            int start = getCaretPosition();
            int end = start + line.length();
            replaceText(start, end, INDENT + line);
            moveTo(orgPos + INDENT.length());
          }
        }
    );
    Nodes.addInputMap(this, im);

    addEventHandler(KeyEvent.KEY_TYPED, e -> {
      String character = e.getCharacter();
      String line = getText(getCurrentParagraph());
      // TODO add option to disable this
      if ("(".equals(character)) {
        if (line.length() == getCaretColumn()) {
          insertTextAndMoveBack(")");
        }
      } else if ("{".equals(character)) {
        String indent = StringUtils.getLeadingSpaces(line);
        if (line.length() == getCaretColumn()) {

          // No new lines for variables e.g. ${project.version} or back quoted
          if (line.endsWith("${") || line.endsWith("`{")) {
            int targetCaretPos = getCaretPosition();
            insertText(targetCaretPos, "}");
            moveTo(targetCaretPos);
          } else {
            insertText(getCaretPosition(), "\n" + indent + INDENT);
            int targetCaretPos = getCaretPosition();
            insertText(targetCaretPos, "\n" + indent + "}");
            moveTo(targetCaretPos);
          }
        }
      } else if ("[".equals(character) && line.length() == getCaretColumn()) {
        insertTextAndMoveBack("]");
      }
    });
  }

  private void insertTextAndMoveBack(String s) {
    int caretPos = getCaretPosition();
    insertText(caretPos, s);
    moveTo(caretPos);
  }

  /**
   *
   * @param selected the selected text
   * @return the text moved back
   */
  protected String backIndentText(String selected) {
    String[] lines = selected.split("\n");
    List<String> untabbed = new ArrayList<>();
    for (String line : lines) {
      if (line.startsWith(INDENT)) {
        untabbed.add(line.substring(2));
      } else {
        untabbed.add(line);
      }
    }
    return String.join("\n", untabbed);
  }

  /**
   * Indent (add space) to the selected area
   * e.g. when pressing the tab button
   *
   * @param selected the text to indent
   * @return the indented text
   */
  protected String indentText(String selected) {
    if (selected == null || "".equals(selected)) {
      return INDENT;
    }
    String[] lines = selected.split("\n");
    List<String> tabbed = new ArrayList<>();
    for (String line : lines) {
      tabbed.add(INDENT + line);
    }
    return String.join("\n", tabbed);
  }

  /**
   *
   * @param text the text to style
   * @return the style spans (highlights)
   */
  protected abstract StyleSpans<Collection<String>> computeHighlighting(String text);

  @Override
  public File getFile() {
    return file;
  }

  @Override
  public void setFile(File file) {
    this.file = file;
  }

  @Override
  public String getTextContent() {
    String code;
    String selected = selectedTextProperty().getValue();
    if (selected == null || "".equals(selected)) {
      code = getText();
    } else {
      code = selected;
    }
    return code;
  }

  @Override
  public String getAllTextContent() {
    return getText();
  }

  @Override
  public void replaceContentText(int start, int end, String text) {
    blockChange = true;
    replaceText(start, end, text);
    blockChange = false;
  }

  @Override
  public void replaceContentText(String content, boolean isReadFromFile) {
    if (isReadFromFile) {
      blockChange = true;
      replaceText(0, getLength(), content);
      blockChange = false;
      parentTab.contentSaved();
    } else {
      replaceText(0, getLength(), content);
    }
  }

  /**
   *
   * @return the atb containing this text area
   */
  public TextAreaTab getParentTab() {
    return parentTab;
  }

  /**
   * Associate this CodeTextArea with a Tab
   *
   * @param parentTab the tab to set as the "owner" of this CodeTextArea
   */
  public void setParentTab(TextAreaTab parentTab) {
    this.parentTab = parentTab;
  }

  /**
   * Attempt to figure out what the user meant and provide suggestions for completion
   */
  public void autoComplete() {
    // do nothing per default
  }

  protected void suggestCompletion(String lastWord, TreeMap<String, Boolean> keyWords, ContextMenu suggestionsPopup) {
    List<CustomMenuItem> menuItems = new LinkedList<>();
    for (Map.Entry<String, Boolean> entry : keyWords.entrySet()) {
      String result = entry.getKey();
      Label entryLabel = new Label(result);
      CustomMenuItem item = new CustomMenuItem(entryLabel, true);
      item.setOnAction(actionEvent -> {
        try {
          String replacement;
          if (lastWord.contains(".")) { // We are selecting from a list of methods
            String start = lastWord.substring(lastWord.lastIndexOf('.') + 1);
            //Gade.instance().getConsoleComponent().getConsole().appendFx("start is " + start + ", result is " + result, true);
            replacement = result.substring(start.length());
          } else { // We are completing a keyword or an object name
            replacement = result.substring(lastWord.length());
          }
          //Gade.instance().getConsoleComponent().getConsole().appendFx("result is " + result + ", replacement is " + replacement, true);
          insertText(getCaretPosition(), replacement);
          int currentParagraph = getCurrentParagraph();
          if (entry.getValue()) {
            int lineEnd = getParagraphLength(currentParagraph);
            int colIdx = replacement.endsWith(")") ? lineEnd - 1 : lineEnd;
            moveTo(currentParagraph, colIdx);
          } else {
            moveTo(currentParagraph, getParagraphLength(currentParagraph));
          }
          suggestionsPopup.hide();
          requestFocus();
        } catch (Throwable t) {
          ExceptionAlert.showAlert("Failed to process suggestion: " + t.getMessage(), t);
        }
      });
      menuItems.add(item);
    }
    suggestionsPopup.getItems().clear();
    suggestionsPopup.getItems().addAll(menuItems);
    double screenX = 0;
    double screenY = 0;
    Optional<Bounds> bounds = this.caretBoundsProperty().getValue();
    if (bounds.isPresent()) {
      Bounds bound = bounds.get();
      screenX = bound.getMaxX();
      screenY = bound.getMaxY();
    }
    suggestionsPopup.setOnHiding(e -> this.requestFocus());
    suggestionsPopup.show(this, screenX, screenY);
  }

  /**
   * compute and set syntax highlighting
   */
  public void highlightSyntax() {
    setStyleSpans(0, computeHighlighting(getText()));
  }
}
