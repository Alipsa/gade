package se.alipsa.gade.interaction;

import javafx.scene.image.Image;
import org.apache.tika.Tika;
import org.girod.javafx.svgimage.SVGImage;
import org.girod.javafx.svgimage.SVGLoader;
import org.girod.javafx.svgimage.xml.parsers.SVGParsingException;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ReadImage {

    /* Note that SVG images are not supported OOTB in javafx */
    public Image read(String name) throws IOException {
        URL url = getResourceUrl(name);
        //System.out.println("Reading image from " + url);
        try {
            if (url == null || !Paths.get(url.toURI()).toFile().exists()) {
                throw new FileNotFoundException("readImage: Failed to find file " + name);
            }
        } catch (URISyntaxException e) {
            // We get the url from the classloader so this should never happen.
            throw new IOException("readImage: Unexpected problem converting the file path to an URI", e);
        }
        String contentType = getContentType(name);
        if ("image/svg+xml".equals(contentType)) {
            try {
                SVGImage result = SVGLoader.load(url);
                return result.toImage();
            } catch (SVGParsingException e) {
                throw new IOException("readImage: Failed to read svg image", e);
            }
        }
        return new Image(url.toExternalForm());
    }

    public Image read(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("The file " + file + " does not exist");
        }

        String contentType = getContentType(file);
        if ("image/svg+xml".equals(contentType)) {
            try {
                SVGImage result = SVGLoader.load(file);
                return result.toImage();
            } catch (SVGParsingException e) {
                throw new IOException("readImage: Failed to read svg image", e);
            }
        }
        return new Image(file.toURI().toURL().toExternalForm());
    }

    /**
     * Find a resource using available class loaders.
     * It will also load resources/files from the
     * absolute path of the file system (not only the classpath's).
     *
     * @param resource the path to the resource
     * @return the URL representation of the resource
     */
    public URL getResourceUrl(String resource) {
        final List<ClassLoader> classLoaders = new ArrayList<>();
        classLoaders.add(Thread.currentThread().getContextClassLoader());
        classLoaders.add(ReadImage.class.getClassLoader());

        for (ClassLoader classLoader : classLoaders) {
            final URL url = getResourceWith(classLoader, resource);
            if (url != null) {
                return url;
            }
        }

        final URL systemResource = ClassLoader.getSystemResource(resource);
        if (systemResource != null) {
            return systemResource;
        } else {
            try {
                return new File(resource).toURI().toURL();
            } catch (MalformedURLException e) {
                return null;
            }
        }
    }

    private URL getResourceWith(ClassLoader classLoader, String resource) {
        if (classLoader != null) {
            return classLoader.getResource(resource);
        }
        return null;
    }

    public String getContentType(String fileName) throws IOException {
        File file = new File(fileName);
       return getContentType(file);
    }

    public String getContentType(File file) throws IOException {
        String fileName = file.getAbsolutePath();
        if (!file.exists()) {
            URL url = getResourceUrl(fileName);
            if (url != null) {
                try {
                    file = Paths.get(url.toURI()).toFile();
                } catch (URISyntaxException e) {
                    // Ignore, the URI comes from the classloader so cannot have a syntax issue
                }
            }
        }
        if (!file.exists()) {
            throw new FileNotFoundException("contentType: " + fileName + " does not exist!");
        }
        Tika tika = new Tika();
        return tika.detect(file);
    }

    public String help() {
        return """
            ReadImage: An Image reader that can read SVG files
            --------------------------------------------------
            Image read(String name)
                create an javafx Image from the url/path specified
                
            URL getResourceUrl(String resource)
                create an url to the path specified
                
            String getContentType(String fileName)
                makes an educated guess of the content type for the filePath specified
            """;
    }

    @Override
    public String toString() {
        return "An Image reader that can read SVG files";
    }
}
