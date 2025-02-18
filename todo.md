# TODO

### gmd save as pdf is broken
- produces a 0 byte file

### Suggestions / Auto complete
- Suggestions / autocomplete relies on a some rather simple code to figure out what is going on and what to suggest.
A more proper approach would be to parse the code up to the current point and inspect the AST to determine correct action.

### Project Filetree improvements
- drag and drop a file in the file tree to move it
- update the filetree when adding a file or saving a file in the tree scope

### Gradle integration
- this is barely tested so probably has several bugs

###  gui testing
- gui tests are quite limited, add a more appropriate javafx gui testing tool, https://github.com/TestFX/TestFX looks promising

### database access through SSH
https://stackoverflow.com/questions/1968293/connect-to-remote-mysql-database-through-ssh-using-java
https://kahimyang.com/kauswagan/code-blogs/1337/ssh-tunneling-with-java-a-database-connection-example
https://cryptofreek.org/2012/06/06/howto-jdbc-over-an-ssh-tunnel/
