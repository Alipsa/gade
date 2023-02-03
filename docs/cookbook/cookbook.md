# Gade cookbook

Gade is an integrated analytics development environment meant to aid you in the process of
gathering data, extract information from that data, and visualize that data to transform that information into knowledge.

A lot of the inspiration for writing this cookbook is drawn from Paul Kings [Groovy Data Science](https://speakerdeck.com/paulk/groovy-data-science)
presentation as well as his excellent [groovy.data-science](https://github.com/paulk-asert/groovy-data-science) project at Github.
The other major inspiration comes from the [Tablesaw documentation](https://jtablesaw.github.io/tablesaw/).
I highly recommend you to read all of these after reading this cookbook to go deeper into the details.

# Table of content
- [Gather](gather.md)
  - [Import from CSV](gather.md/#import-a-csv-file)
  - [Import from Excel](gather.md/#Import-an-excel-file)
  - [Import from database](gather.md/#Import-data-from-a-relational-database)
  - [Import Json](gather.md/#import-json)
  - [Import XML](gather.md/#import-xml)
  - [Import Open Office Calc](gather.md/#import-open-office-calc)
- [Explore](explore.md)
  - [Table info](explore.md/#tableInfo)
  - [Frequency tables](explore.md/#frequencyTables)
  - [Histograms](explore.md/#histograms)
  - [Heat maps](explore.md/#heatMaps)
  - [Box plots](explore.md/#boxPlots)
  - [Scatter Plots](explore.md/#scatterPlots)
- [Clean, merge and transform](cleanMergeTransform.md)
  - [Sort](cleanMergeTransform.md/#sort)
  - [Remove Missing](cleanMergeTransform.md/#removeMissing)
  - [Remove Outliers](cleanMergeTransform.md/#removeOutliers)
  - [Adjust Scales](cleanMergeTransform.md/#adjustScales)
  - [Merge](cleanMergeTransform.md/#merge)
  - [Aggregate](cleanMergeTransform.md/#aggregate)
- [Analyze](analyze.md)
- [Visualize](visualize.md)
- [Machine learning, modelling and regressions](modelling.md)
  - [Linear regression](modelling.md/#linearRegression)
  - [Logistic regression](modelling.md/#logisticRegression)
  - [Poisson Regression](modelling.md/#poissonRegression)
  - [Decision tree regression](modelling.md/#decisionTreeRegression)
  - [Random forest](modelling.md/#randomForest)
  - [K means clustering](modelling.md/#kMeansClustering)
  - [Neural networks](modelling.md/#neuralNetworks)
- [Reporting](reporting.md)
  - [Groovy Markdown](reporting.md/#groovy-markdown)
  - [Save to a spreadsheet](reporting.md/#save-to-spreadsheets)
  - [Save to a presentation (Powerpoint or Impress)](reporting.md/#save-to-a-presentations)
- [Creating libraries](creatingLibraries.md)