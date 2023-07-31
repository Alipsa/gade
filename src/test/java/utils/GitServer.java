package utils;

import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import se.alipsa.gade.utils.FileUtils;

import java.io.File;
import java.io.IOException;

/*
 * Using jgit and Jetty to create a git server that
 * can be used for testing
 */
public class GitServer {

    File localPath;
    Server server;
    GitServlet gitServlet;

    public GitServer() throws Exception {
        Repository repository = createNewRepository();

        populateRepository(repository);

        // Create the JGit Servlet which handles the Git protocol
        gitServlet = new GitServlet();
        gitServlet.setRepositoryResolver((req, name) -> {
            repository.incrementOpen();
            return repository;
        });

    }

    public void start(int port) throws Exception {
        server = new Server(port);

        ServletHandler handler = new ServletHandler();
        server.setHandler(handler);

        ServletHolder holder = new ServletHolder(gitServlet);

        handler.addServletWithMapping(holder, "/*");

        server.start();
    }

    private void populateRepository(Repository repository) throws IOException, GitAPIException {
        // enable pushing to the sample repository via http
        repository.getConfig().setString("http", null, "receivepack", "true");

        try (Git git = new Git(repository)) {
            File myfile = new File(repository.getDirectory().getParent(), "testfile");
            if(!myfile.createNewFile()) {
                throw new IOException("Could not create file " + myfile);
            }

            git.add().addFilepattern("testfile").call();

            System.out.println("Added file " + myfile + " to repository at " + repository.getDirectory());

            git.commit().setMessage("Test-Checkin").call();
        }
    }

    private Repository createNewRepository() throws IOException {
        // prepare a new folder
        localPath = File.createTempFile("TestGitRepository", "");
        System.out.println("Creating git temp repo in " + localPath);
        if(!localPath.delete()) {
            throw new IOException("Could not delete temporary file " + localPath);
        }

        if(!localPath.mkdirs()) {
            throw new IOException("Could not create directory " + localPath);
        }

        // create the directory
        Repository repository = FileRepositoryBuilder.create(new File(localPath, ".git"));
        repository.create();

        return repository;
    }

    public void stop() throws Exception {
        server.stop();
        FileUtils.delete(localPath);
    }
}
