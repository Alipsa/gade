package se.alipsa.gade.code;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.CustomMenuItem;
import javafx.scene.control.IndexRange;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.wellbehaved.event.EventPattern;
import org.fxmisc.wellbehaved.event.InputMap;
import org.fxmisc.wellbehaved.event.Nodes;
import se.alipsa.gade.Gade;
import se.alipsa.gade.UnStyledCodeArea;
import se.alipsa.gade.code.completion.CompletionItem;
import se.alipsa.gade.code.completion.EnhancedCompletion;
import se.alipsa.gade.utils.StringUtils;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static javafx.scene.input.KeyCode.ENTER;
import static se.alipsa.gade.Constants.INDENT;


/**
 * Base class for all code areas
 */
public abstract class CodeTextArea extends UnStyledCodeArea implements TabTextArea {

  /**
   * The file this texarea is editing
   */
  protected File file;

  /**
   * A flag that indicates whether a change of text should be considered
   * as changed text (true), e-g- an edit by the user,
   * or new text (false), e.g read from file
   */
  protected boolean blockChange = false;

  private TextAreaTab parentTab;

  private final Pattern whiteSpace = Pattern.compile("^\\s+");

  // Tracks the currently open completion menu (if any)
  private ContextMenu activeCompletionPopup;

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
      } else if (ENTER.equals(keyCode)) {
        // If completion popup is open, let the popup handler consume Enter (don’t insert newline/indent)
        if (activeCompletionPopup != null && activeCompletionPopup.isShowing()) {
          e.consume();
          return;
        }
        // Maintain indentation on the next line
        Matcher m = whiteSpace.matcher(getParagraph(getCurrentParagraph()).getSegments().getFirst());
        if (m.find()) {
          Platform.runLater(() -> insertText(getCaretPosition(), m.group()));
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
    if (selected == null || selected.isEmpty()) {
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
    if (selected == null || selected.isEmpty()) {
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

  protected void suggestCompletion(String lastWord,
                                   TreeMap<String, Boolean> keyWords,
                                   ContextMenu suggestionsPopup) {
    activeCompletionPopup = suggestionsPopup;
    suggestionsPopup.hide();

    final String initialWord = (lastWord == null ? "" : lastWord);

    java.util.List<CompletionItem> items =
        EnhancedCompletion.suggest(
            initialWord, keyWords, this.getText(), getCaretPosition());
    if (items.isEmpty()) return;

    // Anchor where replacement starts (fixed while popup is open)
    final int anchorStart = Math.max(0, getCaretPosition() - initialWord.length());

    // Full set + filtered view for the ListView
    final ObservableList<CompletionItem> allItems = FXCollections.observableArrayList(items);
    final ObservableList<CompletionItem> viewItems = FXCollections.observableArrayList(allItems);

    final ListView<CompletionItem> listView = new ListView<>(viewItems);
    listView.getStyleClass().add("completion-list");
    listView.setPrefWidth(520);
    listView.setMaxHeight(300);
    listView.setCellFactory(lv -> new ListCell<>() {
      @Override
      protected void updateItem(CompletionItem ci, boolean empty) {
        super.updateItem(ci, empty);
        setText(empty || ci == null ? "" : ci.renderLabel());
      }
    });
    if (!viewItems.isEmpty()) listView.getSelectionModel().select(0);
    listView.setFocusTraversable(false);

    // Filter according to current editor text between anchorStart..caret
    final Runnable refreshFilter = () -> {
      int caret = Math.max(getCaretPosition(), anchorStart);
      String prefix = "";
      try {
        prefix = getText(anchorStart, caret);
      } catch (Exception ignore) {
      }
      final String low = (prefix == null ? "" : prefix).toLowerCase(Locale.ROOT);

      // remember current selection
      var prev = listView.getSelectionModel().getSelectedItem();

      var filtered = allItems.filtered(ci ->
          ci.completion() != null &&
              ci.completion().toLowerCase(Locale.ROOT).startsWith(low));

      viewItems.setAll(filtered);

      if (!viewItems.isEmpty()) {
        int idx = prev != null ? viewItems.indexOf(prev) : -1;
        listView.getSelectionModel().select(Math.max(idx, 0));
        listView.scrollTo(listView.getSelectionModel().getSelectedIndex());
      }
    };

    // Commit currently highlighted suggestion
    final Runnable commitSelected = () -> {
      CompletionItem sel = listView.getSelectionModel().getSelectedItem();
      if (sel == null && !viewItems.isEmpty()) sel = viewItems.getFirst();
      if (sel != null) {
        int caret = getCaretPosition();
        if (caret < anchorStart) caret = anchorStart;
        replaceText(anchorStart, caret, sel.completion());
        suggestionsPopup.hide();
        requestFocus();
      }
    };

    // Mouse double-click commits
    listView.setOnMouseClicked(evt -> {
      if (evt.getClickCount() >= 2) {
        commitSelected.run();
        evt.consume();
      }
    });

    // Show the popup with a single CustomMenuItem
    CustomMenuItem container = new CustomMenuItem(listView, false);
    suggestionsPopup.getItems().setAll(container);

    java.util.Optional<javafx.geometry.Bounds> cb = this.getCaretBounds();
    javafx.geometry.Point2D pos = cb
        .map(b -> new javafx.geometry.Point2D(b.getMaxX(), b.getMaxY()))
        .orElseGet(() -> new javafx.geometry.Point2D(this.getLayoutX(), this.getLayoutY()));
    suggestionsPopup.show(this, pos.getX(), pos.getY());

    // Keep focus on the editor when the menu shows (helps routing keys)
    suggestionsPopup.setOnShown(e -> this.requestFocus());

// Attach nav handler to BOTH the owner scene and the popup scene
    final Scene ownerScene = getScene();

    final EventHandler<KeyEvent> sceneNav = evt -> {
      if (!suggestionsPopup.isShowing()) return;

      switch (evt.getCode()) {
        case DOWN -> {
          listView.getSelectionModel().selectNext();
          // no scrollTo here
          evt.consume();
        }
        case UP -> {
          listView.getSelectionModel().selectPrevious();
          // no scrollTo here
          evt.consume();
        }
        case PAGE_DOWN -> {
          int idx = Math.min(listView.getSelectionModel().getSelectedIndex() + 10,
              Math.max(0, viewItems.size() - 1));
          listView.getSelectionModel().select(idx);
          listView.scrollTo(idx); // OK for paging
          evt.consume();
        }
        case PAGE_UP -> {
          int idx = Math.max(listView.getSelectionModel().getSelectedIndex() - 10, 0);
          listView.getSelectionModel().select(idx);
          listView.scrollTo(idx); // OK for paging
          evt.consume();
        }
        case HOME -> {
          if (!viewItems.isEmpty()) {
            listView.getSelectionModel().select(0);
            listView.scrollTo(0); // OK
          }
          evt.consume();
        }
        case END -> {
          if (!viewItems.isEmpty()) {
            int last = viewItems.size() - 1;
            listView.getSelectionModel().select(last);
            listView.scrollTo(last); // OK
          }
          evt.consume();
        }
        case ENTER -> { commitSelected.run(); evt.consume(); }
        case ESCAPE -> { suggestionsPopup.hide(); evt.consume(); }
        default -> { /* let typing flow */ }
      }
    };

    if (ownerScene != null) {
      ownerScene.addEventFilter(KeyEvent.KEY_PRESSED, sceneNav);
    }
    // The popup scene exists only after show(); add the filter on the next pulse
    Platform.runLater(() -> {
      Scene popupScene = listView.getScene();
      if (popupScene != null) {
        popupScene.addEventFilter(KeyEvent.KEY_PRESSED, sceneNav);
      }
    });

    // Live filtering while typing/backspacing in the editor
    final EventHandler<KeyEvent> typedHandler = evt -> {
      if (!suggestionsPopup.isShowing()) return;

      // Swallow typed Enter so it doesn't refresh the list (and doesn't insert a newline)
      String ch = evt.getCharacter();
      if ("\r".equals(ch) || "\n".equals(ch)) {
        evt.consume(); // <-- this prevents the reset-to-top effect
        return;
      }

      // For normal characters, re-filter after the text has updated
      Platform.runLater(refreshFilter);
    };

    final EventHandler<KeyEvent> backDelHandler = evt -> {
      if (!suggestionsPopup.isShowing()) return;
      if (evt.getCode() == KeyCode.BACK_SPACE
          || evt.getCode() == KeyCode.DELETE) {
        Platform.runLater(refreshFilter);
      }
    };

    // Attach (don’t consume)
    this.addEventFilter(KeyEvent.KEY_TYPED, typedHandler);
    this.addEventFilter(KeyEvent.KEY_PRESSED, backDelHandler);


    suggestionsPopup.setOnHiding(e -> {
      if (ownerScene != null) {
        ownerScene.removeEventFilter(KeyEvent.KEY_PRESSED, sceneNav);
      }
      Scene popupScene = listView.getScene();
      if (popupScene != null) {
        popupScene.removeEventFilter(KeyEvent.KEY_PRESSED, sceneNav);
      }
      this.removeEventFilter(KeyEvent.KEY_TYPED, typedHandler);
      this.removeEventFilter(KeyEvent.KEY_PRESSED, backDelHandler);
      activeCompletionPopup = null;
      this.requestFocus();
    });
  }


  /**
   * compute and set syntax highlighting
   */
  public void highlightSyntax() {
    setStyleSpans(0, computeHighlighting(getText()));
  }

}
