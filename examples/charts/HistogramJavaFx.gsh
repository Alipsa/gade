import java.util.Random;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import se.alipsa.groovy.datasets.*

mpg = Dataset.mtcars()['mpg'] 
DATA_SIZE = mpg.size()
data = mpg.toArray(new double[]{})
group = new int[5]



//count data population in groups
void groupData(){
    for(int i=0; i<5; i++){
        group[i]=0;
    }
    for(int i=0; i<DATA_SIZE; i++){
        if(data[i]<=15){
            group[0]++;
        } else if(data[i]<=20){
            group[1]++;
        } else if(data[i]<=25){
            group[2]++;
        } else if(data[i]<=30){
            group[3]++;
        } else if(data[i]<=35){
            group[4]++;
        }
    }
}


groupData()

CategoryAxis xAxis = new CategoryAxis();
xAxis.setLabel("Range");    
NumberAxis yAxis = new NumberAxis();
yAxis.setLabel("Population");
BarChart<String,Number> barChart = 
    new BarChart<>(xAxis,yAxis);

XYChart.Series series1 = new XYChart.Series();
series1.setName("Histogram");       
series1.getData().add(new XYChart.Data("10-15", group[0]));
series1.getData().add(new XYChart.Data("15-20", group[1]));
series1.getData().add(new XYChart.Data("20-25", group[2]));
series1.getData().add(new XYChart.Data("25-30", group[3]));
series1.getData().add(new XYChart.Data("30-35", group[4])); 

barChart.getData().addAll(series1);

io.display(barChart, "Histogram")