

connectionInfo = io.dbConnection("mydatabase").withPassword(getDbPasswdFromSomewhere())
table = io.dbSelect(connectionInfo, "select * from mytable")