package se.alipsa.gade.runtime;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
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

/**
 * Dialog to create, edit and select runtimes.
 */
public class RuntimeEditorDialog extends Dialog<RuntimeEditorResult> {

  private final ListView<RuntimeConfig> runtimeListView = new ListView<>();
  private final TextField nameField = new TextField();
  private final TextField javaHomeField = new TextField();
  private final TextField groovyHomeField = new TextField();
  private final ListView<String> jarList = new ListView<>();
  private final ListView<String> dependencyList = new ListView<>();

  private final List<RuntimeConfig> customRuntimes;
  private final List<RuntimeConfig> builtIns;

  public RuntimeEditorDialog(Gade gui, RuntimeConfig initialSelection, boolean newRuntime) {
    setTitle("Edit runtimes");
    initStyle(StageStyle.DECORATED);
    builtIns = gui.getRuntimeManager().getBuiltInRuntimes();
    customRuntimes = new ArrayList<>(gui.getRuntimeManager().getCustomRuntimes());

    runtimeListView.setItems(FXCollections.observableArrayList(allRuntimes()));
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
    form.add(new Label("JVM home"), 0, 1);
    form.add(createBrowseField(javaHomeField, true), 1, 1);
    form.add(new Label("Groovy home"), 0, 2);
    form.add(createBrowseField(groovyHomeField, true), 1, 2);
    form.add(new Label("Additional JARs"), 0, 3);
    form.add(createListEditor(jarList, true), 1, 3);
    form.add(new Label("Dependencies"), 0, 4);
    form.add(createListEditor(dependencyList, false), 1, 4);

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

  private void selectRuntime(RuntimeConfig runtime) {
    int index = runtimeListView.getItems().indexOf(runtime);
    if (index >= 0) {
      runtimeListView.getSelectionModel().select(index);
    }
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
    boolean editable = runtime != null && RuntimeType.CUSTOM.equals(runtime.getType());
    nameField.setDisable(!editable);
    javaHomeField.setDisable(!editable);
    groovyHomeField.setDisable(!editable);
    jarList.setDisable(!editable);
    dependencyList.setDisable(!editable);
    if (runtime == null) {
      nameField.clear();
      javaHomeField.clear();
      groovyHomeField.clear();
      jarList.getItems().clear();
      dependencyList.getItems().clear();
      return;
    }
    nameField.setText(runtime.getName());
    javaHomeField.setText(runtime.getJavaHome() == null ? "" : runtime.getJavaHome());
    groovyHomeField.setText(runtime.getGroovyHome() == null ? "" : runtime.getGroovyHome());
    jarList.setItems(FXCollections.observableArrayList(runtime.getAdditionalJars()));
    dependencyList.setItems(FXCollections.observableArrayList(runtime.getDependencies()));
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
    runtimeListView.getItems().add(config);
    runtimeListView.getSelectionModel().select(config);
  }

  private boolean nameExists(String name) {
    return allRuntimes().stream().anyMatch(r -> r.getName().equalsIgnoreCase(name));
  }

  private void deleteRuntime() {
    RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
    if (selected == null || !RuntimeType.CUSTOM.equals(selected.getType())) {
      return;
    }
    customRuntimes.removeIf(r -> r.getName().equalsIgnoreCase(selected.getName()));
    runtimeListView.getItems().remove(selected);
    runtimeListView.getSelectionModel().selectFirst();
  }

  private void saveRuntime() {
    RuntimeConfig selected = runtimeListView.getSelectionModel().getSelectedItem();
    if (selected == null || !RuntimeType.CUSTOM.equals(selected.getType())) {
      return;
    }
    String name = nameField.getText().trim();
    if (name.isBlank()) {
      ExceptionAlert.showAlert("Name is required for a runtime", new IllegalArgumentException("Name missing"));
      return;
    }
    if (!selected.getName().equalsIgnoreCase(name) && nameExists(name)) {
      ExceptionAlert.showAlert("Runtime name must be unique", new IllegalArgumentException("Duplicate name"));
      return;
    }
    RuntimeConfig updated = new RuntimeConfig(
        name,
        RuntimeType.CUSTOM,
        javaHomeField.getText().trim(),
        groovyHomeField.getText().trim(),
        new ArrayList<>(jarList.getItems()),
        new ArrayList<>(dependencyList.getItems())
    );
    replaceCustom(selected, updated);
    replaceInList(selected, updated);
    runtimeListView.getSelectionModel().select(updated);
  }

  private void replaceCustom(RuntimeConfig previous, RuntimeConfig updated) {
    int idx = -1;
    for (int i = 0; i < customRuntimes.size(); i++) {
      if (customRuntimes.get(i).getName().equalsIgnoreCase(previous.getName())) {
        idx = i;
        break;
      }
    }
    if (idx >= 0) {
      customRuntimes.set(idx, updated);
    }
  }

  private void replaceInList(RuntimeConfig previous, RuntimeConfig updated) {
    int idx = runtimeListView.getItems().indexOf(previous);
    if (idx >= 0) {
      runtimeListView.getItems().set(idx, updated);
    }
  }
}
