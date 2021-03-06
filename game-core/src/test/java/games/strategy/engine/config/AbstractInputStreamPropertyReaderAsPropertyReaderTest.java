package games.strategy.engine.config;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;
import java.util.Properties;

import games.strategy.io.IoUtils;

final class AbstractInputStreamPropertyReaderAsPropertyReaderTest extends AbstractPropertyReaderTestCase {
  @Override
  protected PropertyReader createPropertyReader(final Map<String, String> properties) throws Exception {
    final Properties props = new Properties();
    props.putAll(properties);
    final byte[] bytes = IoUtils.writeToMemory(os -> {
      props.store(os, null);
    });

    return new AbstractInputStreamPropertyReader("propertySourceName") {
      @Override
      protected InputStream newInputStream() {
        return new ByteArrayInputStream(bytes);
      }
    };
  }
}
