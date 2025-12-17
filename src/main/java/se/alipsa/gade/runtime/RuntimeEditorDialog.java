package se.alipsa.gade.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.StageStyle;
import se.alipsa.gade.Gade;
import se.alipsa.gade.runtime.RuntimeType;
import se.alipsa.gade.utils.ExceptionAlert;
import se.alipsa.gade.utils.GuiUtils;

/**
 * Dialog to create, edit and select runtimes.
 */
public class RuntimeEditorDialog extends Dialog<RuntimeEditorResult> {

  private final ListView<RuntimeConfig> runtimeListView = new ListView<>();
  private final javafx.collections.ObservableList<RuntimeConfig> runtimeItems = FXCollections.observableArrayList();
  private final TextField nameField = new TextField();
  private final ComboBox<RuntimeType> typeBox = new ComboBox<>();
  private final TextField javaHomeField = new TextField();
  private final TextField groovyHomeField = new TextField();
  private final ListView<String> jarList = new ListView<>();
  private final ListView<String> dependencyList = new ListView<>();

  private final Label groovyHomeLabel = new Label("Groovy home");
  private final Label additionalJarsLabel = new Label("Additional JARs");
  private final Label dependenciesLabel = new Label("Dependencies");
  private final Node groovyHomeNode = createBrowseField(groovyHomeField, true);
  private final Node additionalJarsNode = createListEditor(jarList, true);
  private final Node dependenciesNode = createListEditor(dependencyList, false);

  private List<RuntimeConfig> customRuntimes;
  private final List<RuntimeConfig> builtIns;

  public RuntimeEditorDialog(Gade gui, RuntimeConfig initialSelection, boolean newRuntime) {
    setTitle("Edit runtimes");
    initStyle(StageStyle.DECORATED);
    GuiUtils.addStyle(gui, this);
    builtIns = gui.getRuntimeManager().getBuiltInRuntimes();
    customRuntimes = new ArrayList<>(gui.getRuntimeManager().getCustomRuntimes());

    runtimeListView.setItems(runtimeItems);
    runtimeItems.setAll(mergedRuntimes());
    runtimeListView.setCellFactory(listView -> new ListCell<>() {
      @Override
      protected void updateItem(RuntimeConfig item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
        } else {
          setText(item.getName() + " [" + item.getType() + "]");
        }
      }
    });

    ButtonType useSelected = new ButtonType("Use selected", ButtonBar.ButtonData.OK_DONE);
    ButtonType close = new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE);
    getDialogPane().getButtonTypes().addAll(useSelected, close);

    Button useSelectedButton = (Button) getDialogPane().lookupButton(useSelected);
    useSelectedButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
      if (!applyPendingEditsForSelected()) {
        event.consume();
      }
    });

    Button addButton = new Button("Add");
    addButton.setOnAction(e -> addRuntime());

    Button deleteButton = new Button("Delete");
    deleteButton.setOnAction(e -> deleteRuntime());

    Button saveButton = new Button("Save");
    saveButton.setOnAction(e -> saveRuntime());

    HBox buttons = new HBox(5, addButton, deleteButton, saveButton);
    buttons.setPadding(new Insets(5));

    GridPane form = new GridPane();
    form.setPadding(new Insets(5));
    form.setHgap(5);
    form.setVgap(5);

    form.add(new Label("Name"), 0, 0);
    form.add(nameField, 1, 0);
    form.add(new Label("Type"), 0, 1);
    typeBox.getItems().addAll(RuntimeType.MAVEN, RuntimeType.GRADLE, RuntimeType.CUSTOM);
    form.add(typeBox, 1, 1);
    form.add(new Label("JVM home"), 0, 2);
    form.add(createBrowseField(javaHomeField, true), 1, 2);
    form.add(groovyHomeLabel, 0, 3);
    form.add(groovyHomeNode, 1, 3);
    form.add(additionalJarsLabel, 0, 4);
    form.add(additionalJarsNode, 1, 4);
    form.add(dependenciesLabel, 0, 5);
    form.add(dependenciesNode, 1, 5);

    BorderPane pane = new BorderPane();
    pane.setLeft(runtimeListView);
    pane.setCenter(form);
    pane.setBottom(buttons);
    BorderPane.setMargin(runtimeListView, new Insets(5));
    BorderPane.setMargin(form, new Insets(5));
    runtimeListView.setPrefWidth(200);

    getDialogPane().setContent(pane);
    getDialogPane().setPrefSize(800, 500);

    runtimeListView.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> populateFields(newVal));
    typeBox.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> applyTypeVisibility(newVal));

    if (newRuntime) {
      addRuntime();
    } else if (initialSelection != null) {
      selectRuntime(initialSelection);
    } else {
      runtimeListView.getSelectionModel().selectFirst();
    }

    setResultConverter(buttonType -> {
      if (buttonType == useSelected) {
        RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
        return new RuntimeEditorResult(new ArrayList<>(customRuntimes), selected);
      }
      return new RuntimeEditorResult(new ArrayList<>(customRuntimes), null);
    });
  }

  private List<RuntimeConfig> allRuntimes() {
    List<RuntimeConfig> runtimes = new ArrayList<>(builtIns);
    runtimes.addAll(customRuntimes);
    return runtimes;
  }

  private List<RuntimeConfig> mergedRuntimes() {
    Map<String, RuntimeConfig> merged = new LinkedHashMap<>();
    for (RuntimeConfig r : builtIns) {
      merged.putIfAbsent(r.getName().toLowerCase(Locale.ROOT), r);
    }
    for (RuntimeConfig r : customRuntimes) {
      merged.put(r.getName().toLowerCase(Locale.ROOT), r);
    }
    return new ArrayList<>(merged.values());
  }

  private void selectRuntime(RuntimeConfig runtime) {
    if (runtime == null) {
      return;
    }
    for (RuntimeConfig item : runtimeItems) {
      if (item.getName().equalsIgnoreCase(runtime.getName())) {
        runtimeListView.getSelectionModel().select(item);
        return;
      }
    }
    runtimeListView.getSelectionModel().selectFirst();
  }

  private HBox createBrowseField(TextField field, boolean directory) {
    Button browse = new Button("Browse");
    browse.setOnAction(e -> {
      if (directory) {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select directory");
        File selected = chooser.showDialog(field.getScene().getWindow());
        if (selected != null) {
          field.setText(selected.getAbsolutePath());
        }
      } else {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Select file");
        File selected = chooser.showOpenDialog(field.getScene().getWindow());
        if (selected != null) {
          field.setText(selected.getAbsolutePath());
        }
      }
    });
    HBox box = new HBox(5, field, browse);
    HBox.setHgrow(field, Priority.ALWAYS);
    return box;
  }

  private BorderPane createListEditor(ListView<String> listView, boolean fileChooser) {
    Button add = new Button("Add");
    Button remove = new Button("Remove");
    add.setOnAction(e -> {
      if (fileChooser) {
        FileChooser chooser = new FileChooser();
        File file = chooser.showOpenDialog(listView.getScene().getWindow());
        if (file != null) {
          listView.getItems().add(file.getAbsolutePath());
        }
      } else {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Add dependency");
        dialog.setHeaderText("Enter dependency in group:artifact:version format");
        dialog.showAndWait().ifPresent(text -> {
          if (!text.isBlank()) {
            listView.getItems().add(text.trim());
          }
        });
      }
    });
    remove.setOnAction(e -> {
      int idx = listView.getSelectionModel().getSelectedIndex();
      if (idx >= 0) {
        listView.getItems().remove(idx);
      }
    });
    BorderPane pane = new BorderPane();
    pane.setCenter(listView);
    pane.setBottom(new HBox(5, add, remove));
    BorderPane.setMargin(listView, new Insets(5));
    return pane;
  }

  private void populateFields(RuntimeConfig runtime) {
    boolean editable = runtime != null && !RuntimeType.GADE.equals(runtime.getType());
    nameField.setDisable(!editable);
    typeBox.setDisable(!editable);
    javaHomeField.setDisable(!editable);
    groovyHomeField.setDisable(!editable);
    jarList.setDisable(!editable);
    dependencyList.setDisable(!editable);
    if (runtime == null) {
      nameField.clear();
      typeBox.getSelectionModel().clearSelection();
      javaHomeField.clear();
      groovyHomeField.clear();
      jarList.getItems().clear();
      dependencyList.getItems().clear();
      applyTypeVisibility(null);
      return;
    }
    nameField.setText(runtime.getName());
    typeBox.getSelectionModel().select(runtime.getType());
    javaHomeField.setText(runtime.getJavaHome() == null ? "" : runtime.getJavaHome());
    groovyHomeField.setText(runtime.getGroovyHome() == null ? "" : runtime.getGroovyHome());
    jarList.setItems(FXCollections.observableArrayList(runtime.getAdditionalJars()));
    dependencyList.setItems(FXCollections.observableArrayList(runtime.getDependencies()));
    applyTypeVisibility(runtime.getType());
  }

  private void applyTypeVisibility(RuntimeType type) {
    RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
    boolean editable = selected != null && !RuntimeType.GADE.equals(selected.getType());
    boolean showCustomFields = editable && RuntimeType.CUSTOM.equals(type);

    setVisibleAndManaged(showCustomFields, groovyHomeLabel, groovyHomeNode, additionalJarsLabel, additionalJarsNode,
        dependenciesLabel, dependenciesNode);
    groovyHomeField.setDisable(!showCustomFields);
    jarList.setDisable(!showCustomFields);
    dependencyList.setDisable(!showCustomFields);
  }

  private void setVisibleAndManaged(boolean visible, Node... nodes) {
    for (Node node : nodes) {
      node.setVisible(visible);
      node.setManaged(visible);
    }
  }

  private void addRuntime() {
    String baseName = "Custom runtime";
    int idx = 1;
    String name = baseName;
    while (nameExists(name)) {
      name = baseName + " " + idx++;
    }
    RuntimeConfig config = new RuntimeConfig(name, RuntimeType.CUSTOM, "", "",
        new ArrayList<>(), new ArrayList<>());
    customRuntimes.add(config);
    refreshRuntimeList(config);
  }

  private boolean nameExists(String name) {
    return allRuntimes().stream().anyMatch(r -> r.getName().equalsIgnoreCase(name));
  }

  private void deleteRuntime() {
    RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
    if (selected == null || RuntimeType.GADE.equals(selected.getType())) {
      return;
    }
    boolean removed = customRuntimes.removeIf(r -> r.getName().equalsIgnoreCase(selected.getName()));
    if (removed) {
      refreshRuntimeList(selected);
    }
  }

  private void saveRuntime() {
    RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
    if (selected == null || RuntimeType.GADE.equals(selected.getType())) {
      return;
    }
    RuntimeConfig updated = buildRuntimeFromFields(selected);
    if (updated == null) {
      return;
    }
    replaceCustom(selected, updated);
    refreshRuntimeList(updated);
  }

  private void replaceCustom(RuntimeConfig previous, RuntimeConfig updated) {
    customRuntimes = customRuntimes.stream()
        .filter(r -> !r.getName().equalsIgnoreCase(previous.getName()))
        .collect(Collectors.toCollection(ArrayList::new));
    if (!RuntimeType.GADE.equals(updated.getType())) {
      customRuntimes.add(updated);
    }
  }

  private void refreshRuntimeList(RuntimeConfig preferredSelection) {
    String selectionName = null;
    if (preferredSelection != null) {
      selectionName = preferredSelection.getName();
    } else {
      RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
      if (selected != null) {
        selectionName = selected.getName();
      }
    }
    runtimeItems.setAll(mergedRuntimes());
    if (selectionName != null) {
      for (RuntimeConfig runtime : runtimeItems) {
        if (runtime.getName().equalsIgnoreCase(selectionName)) {
          runtimeListView.getSelectionModel().select(runtime);
          return;
        }
      }
    }
    runtimeListView.getSelectionModel().selectFirst();
  }

  private RuntimeConfig buildRuntimeFromFields(RuntimeConfig selected) {
    if (RuntimeType.GADE.equals(selected.getType())) {
      return selected;
    }
    String name = nameField.getText().trim();
    if (name.isBlank()) {
      ExceptionAlert.showAlert("Name is required for a runtime", new IllegalArgumentException("Name missing"));
      return null;
    }
    if (!selected.getName().equalsIgnoreCase(name) && nameExists(name)) {
      ExceptionAlert.showAlert("Runtime name must be unique", new IllegalArgumentException("Duplicate name"));
      return null;
    }
    RuntimeType type = typeBox.getSelectionModel().getSelectedItem();
    if (type == null) {
      ExceptionAlert.showAlert("Runtime type must be selected", new IllegalArgumentException("Type missing"));
      return null;
    }
    String javaHome = javaHomeField.getText().trim();
    if (!RuntimeType.CUSTOM.equals(type)) {
      return new RuntimeConfig(name, type, javaHome, "", List.of(), List.of());
    }
    return new RuntimeConfig(
        name,
        type,
        javaHome,
        groovyHomeField.getText().trim(),
        new ArrayList<>(jarList.getItems()),
        new ArrayList<>(dependencyList.getItems())
    );
  }

  private boolean applyPendingEditsForSelected() {
    RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
    if (selected == null || RuntimeType.GADE.equals(selected.getType())) {
      return true;
    }
    RuntimeConfig updated = buildRuntimeFromFields(selected);
    if (updated == null) {
      return false;
    }
    replaceCustom(selected, updated);
    refreshRuntimeList(updated);
    return true;
  }
}
