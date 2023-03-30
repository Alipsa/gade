package se.alipsa.gade.utils;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.AreaChart;
import javafx.scene.chart.Chart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DeepCopier {

  public static Serializable deepCopy(Serializable object) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(bos);
    oos.writeObject(object);
    oos.flush();
    oos.close();
    bos.close();
    byte[] byteData = bos.toByteArray();
    ByteArrayInputStream bais = new ByteArrayInputStream(byteData);
    return (Serializable) new ObjectInputStream(bais).readObject();
  }

  public static Scene deepCopy(Scene scene) {
    var root = (Parent)deepCopy(scene.getRoot());
    return new Scene(root, scene.getWidth(), scene.getHeight(), scene.getFill());
  }

  public static Node deepCopy(Node node) {
    if (node == null) {
      return null;
    }
    if (node instanceof Group group) {
      return deepCopy(group);
    }
    if (node instanceof Region region) {
      return deepCopy(region);
    }
    throw new RuntimeException("Unknown node type: " + node.getClass());
  }

  public static Group deepCopy(Group node) {

    var children = node.getChildren();
    List<Node> clonedChildren = new ArrayList<>();
    for (var child : children) {
      clonedChildren.add(deepCopy(child));
    }
    return new Group(clonedChildren);
  }

  public static Region deepCopy(Region node) {
    var children = node.getChildrenUnmodifiable();
    List<Node> clonedChildren = new ArrayList<>();
    for (var child : children) {
      clonedChildren.add(deepCopy(child));
    }
    Node[] nodes = new Node[clonedChildren.size()];
    if (node instanceof StackPane) {
      return new StackPane(clonedChildren.toArray(nodes));
    }
    if (node instanceof HBox) {
      return new HBox(clonedChildren.toArray(nodes));
    }
    if (node instanceof VBox) {
      return new VBox(clonedChildren.toArray(nodes));
    }
    if (node instanceof TilePane) {
      return new TilePane(clonedChildren.toArray(nodes));
    }
    if (node instanceof FlowPane) {
      return new FlowPane(clonedChildren.toArray(nodes));
    }
    if (node instanceof BorderPane) {
      Node center = deepCopy(((BorderPane) node).getCenter());
      Node top  = deepCopy(((BorderPane) node).getTop());
      Node right = deepCopy(((BorderPane) node).getRight());
      Node bottom = deepCopy(((BorderPane) node).getBottom());
      Node left = deepCopy(((BorderPane) node).getLeft());
      return new BorderPane(center,top, right, bottom, left);
    }
    if (node instanceof GridPane) {
      // TODO: create a gridpane deepCopier
      return new GridPane();
    }
    if (node instanceof AnchorPane) {
      return new AnchorPane(clonedChildren.toArray(nodes));
    }
    if (node instanceof Control control) {
      return deepCopy(control);
    }
    if (node instanceof Chart chart) {
      return deepCopy(chart);
    }
    throw new RuntimeException("Support for " + node.getClass() + " with parent " + node.getParent() + " not implemented!");
  }

  public static Control deepCopy(Control control) {
    if (control instanceof Label label) {
      return deepCopy(label);
    }
    throw new RuntimeException("Support for control " + control.getClass() + " not implemented!");
  }

  public static Label deepCopy(Label label) {
    return new Label(label.getText(), label.getGraphic());
  }

  public static Chart deepCopy(Chart chart) {
    if (chart instanceof AreaChart<?,?> areaChart) {
      return deepCopy(areaChart);
    }
    throw new RuntimeException("Support for chart " + chart.getClass() + " not implemented!");
  }

  public static AreaChart<?,?> deepCopy(AreaChart<?,?> chart) {
    AreaChart<?,?> copy = new AreaChart<>(chart.getXAxis(), chart.getYAxis());
    copy.setTitle(chart.getTitle());
    for (var series : chart.getData()) {
      XYChart.Series serie = new XYChart.Series<>();
      serie.setName(series.getName());
      for (var data : series.getData()) {
        serie.getData().add(new XYChart.Data(data.getXValue(), data.getYValue()));
      }
      copy.getData().add(serie);
    }
    copy.setStyle(chart.getStyle());
    copy.setCreateSymbols(chart.getCreateSymbols());
    Background background = chart.getBackground();
    if (background != null) {
      Background bg = new Background(background.getFills(), background.getImages());
      copy.setBackground(bg);
    }

    return copy;
  }
}
