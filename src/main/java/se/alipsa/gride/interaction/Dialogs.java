package se.alipsa.gride.interaction;

import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.FlowPane;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.StringConverter;
import se.alipsa.gride.Gride;
import se.alipsa.gride.utils.GuiUtils;
import se.alipsa.ymp.YearMonthPicker;

import java.io.File;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

public class Dialogs implements GuiInteraction {

    private final Stage stage;

    /**
     * @param stage is the owner of the dialogs, typically the primary stage of the javafx application
     */
    public Dialogs(Stage stage) {
        this.stage = stage;
    }

    public String prompt(String title, String headerText, String message, String defaultValue) throws InterruptedException, ExecutionException {
        FutureTask<String> task = new FutureTask<>(() -> {
            TextInputDialog dialog = new TextInputDialog(defaultValue);
            dialog.setTitle(title);
            dialog.setHeaderText(headerText);
            dialog.setContentText(message);
            dialog.setResizable(true);
            GuiUtils.addStyle(Gride.instance(), dialog);
            return dialog.showAndWait().orElse(null);
        });
        Platform.runLater(task);
        return task.get();
    }

    public Object promptSelect(String title, String headerText, String message, List<Object> options, Object defaultValue) throws InterruptedException, ExecutionException {
        int defaultIndex = options.indexOf(defaultValue);
        if (defaultIndex == -1) {
           defaultIndex = 0; // if we cannot find a match, choose the first item as the default
        }

        final int index = defaultIndex;
        FutureTask<Object> task = new FutureTask<>(() -> {
            ChoiceDialog<Object> dialog = new ChoiceDialog<>(options.get(index), options);
            dialog.setTitle(title);
            dialog.setHeaderText(headerText);
            dialog.setContentText(message);
            dialog.setResizable(true);
            GuiUtils.addStyle(Gride.instance(), dialog);
            return dialog.showAndWait().orElse(null);
        });
        Platform.runLater(task);
        return task.get();
    }

    public LocalDate promptDate(String title, String message, LocalDate defaultValue) throws InterruptedException, ExecutionException {
        FutureTask<LocalDate> task = new FutureTask<>(() -> {
            Dialog<LocalDate> dialog = new Dialog<>();
            dialog.setTitle(title);
            FlowPane content = new FlowPane();
            content.setHgap(5);
            content.getChildren().add(new Label(message));
            DatePicker picker;
            if (defaultValue == null) {
                picker = new DatePicker();
            } else {
                picker = new DatePicker(defaultValue);
            }
            content.getChildren().add(picker);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setResultConverter(buttonType -> {
                if (buttonType == ButtonType.OK) {
                    return picker.getValue();
                }
                return null;
            });
            dialog.setResizable(true);
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
            GuiUtils.addStyle(Gride.instance(), dialog);
            return dialog.showAndWait().orElse(null);
        });
        Platform.runLater(task);
        return task.get();
    }

    public YearMonth promptYearMonth(String title, String message, YearMonth from, YearMonth to, YearMonth initial)
            throws InterruptedException, ExecutionException {
        FutureTask<YearMonth> task = new FutureTask<>(() -> {
            Dialog<YearMonth> dialog = new Dialog<>();
            dialog.setTitle(title);
            FlowPane content = new FlowPane();
            content.setHgap(5);
            content.getChildren().add(new Label(message));
            YearMonthPicker picker = new YearMonthPicker(from, to, initial, Locale.getDefault(), "yyyy-MM");
            content.getChildren().add(picker);
            dialog.getDialogPane().setContent(content);
            dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            dialog.setResultConverter(buttonType -> {
                if (buttonType == ButtonType.OK) {
                    return picker.getValue();
                }
                return null;
            });
            dialog.setResizable(true);
            dialog.getDialogPane().getScene().getWindow().sizeToScene();
            GuiUtils.addStyle(Gride.instance(), dialog);
            return dialog.showAndWait().orElse(null);
        });
        Platform.runLater(task);
        return task.get();
    }

    public File chooseFile(String title, String initialDirectory, String description, String... extensions) throws InterruptedException, ExecutionException {
        FutureTask<File> task = new FutureTask<>(() -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(title == null ? "Select file" : title);
            if (initialDirectory != null) {
                chooser.setInitialDirectory(new File(initialDirectory));
            }
            chooser.getExtensionFilters().addAll(
                    new FileChooser.ExtensionFilter(description, extensions)
            );
            return chooser.showOpenDialog(stage);
        });
        Platform.runLater(task);
        return task.get();
    }

    public File chooseDir(String title, String initialDirectory) throws InterruptedException, ExecutionException  {
        FutureTask<File> task = new FutureTask<>(() -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setInitialDirectory(new File(initialDirectory));
            chooser.setTitle(title);
            return chooser.showDialog(stage);
        });
        Platform.runLater(task);
        return task.get();
    }

    public String help() {
        return """
            Dialogs: User input utilities
            -----------------------------
            String prompt(String title, String headerText, String message, String defaultValue)
               prompt for text input
               
            String promptSelect(String title, String headerText, String message, List<Object> options, Object defaultValue)
                prompt user to pick from a list of values
                
            LocalDate promptDate(String title, String message, LocalDate defaultValue)
                prompt user for a date. If outputformat is null, the format will be yyyy-MM-dd
                
            YearMonth promptYearMonth(String title, String message, YearMonth from, YearMonth to, YearMonth initial)
                prompt user for a yearMonth (yyyy-MM)
                
            File chooseFile(String title, String initialDirectory, String description, String... extensions)
                asks user to select a file
                Returns the java.io.File selected by the user
                
            File chooseDir(String title, String initialDirectory)
                asks user to select a dir
                Return the java.io.File pointing to the directory chosen
            """;
    }
}
