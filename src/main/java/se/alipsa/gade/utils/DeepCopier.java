package se.alipsa.gade.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.chart.*;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Shape;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class DeepCopier {

  private static final Logger log = LogManager.getLogger(DeepCopier.class);

  public static <T> T deepCopy(T obj) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    StringWriter sw = new StringWriter();
    mapper.writeValue(sw, obj);
    return mapper.readValue(sw.toString(), (Class<T>) obj.getClass());
  }

  public static <T extends Serializable> T deepCopy(Serializable object) throws IOException, ClassNotFoundException {
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
    ObjectOutputStream oos = new ObjectOutputStream(bos);
    oos.writeObject(object);
    oos.flush();
    oos.close();
    bos.close();
    byte[] byteData = bos.toByteArray();
    ByteArrayInputStream bais = new ByteArrayInputStream(byteData);
    return (T) new ObjectInputStream(bais).readObject();
  }

  public static Scene deepCopy(Scene scene) {
    var root = (Parent)deepCopy(scene.getRoot());
    return new Scene(root, scene.getWidth(), scene.getHeight(), scene.getFill());
  }

  public static <T extends Node> T deepCopy(Node node) {
    if (node == null) {
      return null;
    }
    if (node instanceof Group group) {
      return (T)deepCopy(group);
    }
    if (node instanceof Region region) {
      return (T)deepCopy(region);
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

  public static <T extends Region> T deepCopy(Region node) {

    if (node instanceof Pane pane) {
      return (T)deepCopy(pane);
    }
    if (node instanceof Chart chart) {
      return (T)deepCopy(chart);
    }
    if (node instanceof Control control) {
      return (T)deepCopy(control);
    }

    log.warn("Support for " + node.getTypeSelector() + " with parent " + node.getParent() + " not implemented!");
    return (T)node;
  }

  public static Control deepCopy(Control control) {
    if (control instanceof Label label) {
      return deepCopy(label);
    }
    throw new RuntimeException("Support for control " + control.getClass() + " not implemented!");
  }

  public static <T extends Pane> T deepCopy(Pane node) {
    var children = node.getChildrenUnmodifiable();
    List<Node> clonedChildren = new ArrayList<>();
    for (var child : children) {
      clonedChildren.add(deepCopy(child));
    }
    Node[] nodes = new Node[clonedChildren.size()];
    if (node instanceof StackPane) {
      return (T)new StackPane(clonedChildren.toArray(nodes));
    }
    if (node instanceof HBox) {
      return (T)new HBox(clonedChildren.toArray(nodes));
    }
    if (node instanceof VBox) {
      return (T)new VBox(clonedChildren.toArray(nodes));
    }
    if (node instanceof TilePane) {
      return (T)new TilePane(clonedChildren.toArray(nodes));
    }
    if (node instanceof FlowPane) {
      return (T)new FlowPane(clonedChildren.toArray(nodes));
    }
    if (node instanceof BorderPane) {
      Node center = deepCopy(((BorderPane) node).getCenter());
      Node top  = deepCopy(((BorderPane) node).getTop());
      Node right = deepCopy(((BorderPane) node).getRight());
      Node bottom = deepCopy(((BorderPane) node).getBottom());
      Node left = deepCopy(((BorderPane) node).getLeft());
      return (T)new BorderPane(center,top, right, bottom, left);
    }
    if (node instanceof GridPane gridPane) {
      // v1.1 FEATURE: Implement GridPane deep copying with row/column constraints preservation.
      throw new RuntimeException("Support for GridPane not yet implemented!");
    }
    if (node instanceof AnchorPane) {
      return (T)new AnchorPane(clonedChildren.toArray(nodes));
    }
    throw new RuntimeException("Support for Pane " + node.getClass() + " not implemented!");
  }

  public static Label deepCopy(Label label) {
    return new Label(label.getText(), label.getGraphic());
  }

  /*public static LabeledText deepCopy(LabeledText labeledText) {
    return labeledText;
  }*/

  public static <T extends Chart> T deepCopy(Chart chart) {
    if (chart instanceof AreaChart areaChart) {
      return (T)deepCopy(areaChart);
    }
    if (chart instanceof BarChart barChart) {
      return (T)deepCopy(barChart);
    }
    if (chart instanceof PieChart pieChart) {
      return (T)deepCopy(pieChart);
    }
    log.warn("Support for chart " + chart.getClass() + " not implemented!");
    return (T)chart;
  }

  public static AreaChart deepCopy(AreaChart<?,?> chart) {
    AreaChart copy = new AreaChart(chart.getXAxis(), chart.getYAxis());
    copyXYChart(chart, copy);
    copy.setCreateSymbols(chart.getCreateSymbols());
    return copy;
  }

  private static Axis<?> copyAxis(Axis axis) {
    if (axis instanceof CategoryAxis categoryAxis) {
      List<String> categories = new ArrayList<>();
      categories.addAll(categoryAxis.getCategories());
      CategoryAxis copy = new CategoryAxis();
      copy.getCategories().addAll(categories);
      return copy;
    }
    if (axis instanceof NumberAxis numberAxis ) {
      if (numberAxis.isAutoRanging()) {
        return new NumberAxis();
      } else {
        return new NumberAxis(numberAxis.getLowerBound(), numberAxis.getUpperBound(), numberAxis.getTickUnit());
      }
    }
    throw new RuntimeException("Support for axis " + axis.getClass() + " not implemented!");
  }

  public static <T,E> BarChart<T,E> deepCopy(BarChart<T,E> chart) {
    Axis<T> xAxis = (Axis<T>) copyAxis(chart.getXAxis());
    Axis<E> yAxis = (Axis<E>) copyAxis(chart.getYAxis());
    BarChart<T,E> copy = new BarChart<>(xAxis, yAxis);
    copyXYChart(chart, copy);
    return copy;
  }

  public static PieChart deepCopy(PieChart chart) {
    PieChart copy = new PieChart();
    for (var data : chart.getData()) {
      copy.getData().add(new PieChart.Data(data.getName(), data.getPieValue()));
    }
    return copy;
  }

  private static void copyXYChart(XYChart<?,?> from, XYChart<?,?> to) {
    to.setTitle(from.getTitle());
    for (var fromSeries : from.getData()) {
      XYChart.Series toSerie = new XYChart.Series<>();
      toSerie.setName(fromSeries.getName());
      for (var data : fromSeries.getData()) {
        toSerie.getData().add(new XYChart.Data(data.getXValue(), data.getYValue()));
      }
      to.getData().add(toSerie);
    }
    to.setStyle(from.getStyle());
    Background background = from.getBackground();
    if (background != null) {
      Background bg = new Background(background.getFills(), background.getImages());
      to.setBackground(bg);
    }
  }
}
