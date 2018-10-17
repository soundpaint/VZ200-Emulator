package emulator.vz200;

import java.io.File;
import java.io.IOException;

public interface CassetteTransportListener {
  public void startPlaying(File file) throws IOException;
  public void startRecording(File file) throws IOException;
  public void stop();
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
