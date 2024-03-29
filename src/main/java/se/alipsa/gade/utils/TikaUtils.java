package se.alipsa.gade.utils;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.tika.parser.txt.CharsetDetector;
import org.apache.tika.parser.txt.CharsetMatch;

import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;

/**
 * We need to initialize config when using Tika otherwise we get warnings about optional dependencies not being available, e.g:
 * WARNING: J2KImageReader not loaded. JPEG2000 files will not be processed.
 */
public class TikaUtils {

  private static final Logger log = LogManager.getLogger();

  private static final TikaUtils INSTANCE = new TikaUtils();

  private final org.apache.tika.Tika apacheTika;

  public static TikaUtils instance() {
    return INSTANCE;
  }

  private TikaUtils() {
    apacheTika = new org.apache.tika.Tika();
  }

  /**
   *
   * @param content the content as a byte array
   * @param context e.g. a file name
   * @return the most likely charset
   */
  public Charset detectCharset(byte[] content, String context) {
    CharsetMatch match = new CharsetDetector().setText(content).detect();
    log.debug("Charset for {} detected as {} with {}% confidence", context, match.getName(), match.getConfidence());
    return Charset.forName(match.getName());
  }

  public Charset detectCharset(File file) throws IOException {
    /* This is not as reliable as using the CharsetDetector so commenting it out
    try(InputStream is = TikaInputStream.get(Files.newInputStream(file.toPath()))) {
      return config.getEncodingDetector().detect(is, new Metadata());
    }
    */
    byte[] textBytes = FileUtils.readFileToByteArray(file);
    return detectCharset(textBytes, file.getName());
  }

  public String detectContentType(File file) throws IOException {
    return apacheTika.detect(file);
  }
}
