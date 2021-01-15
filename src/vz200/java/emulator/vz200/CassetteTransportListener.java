package emulator.vz200;

import java.io.File;
import java.io.IOException;

public interface CassetteTransportListener
{
  void cassetteStartPlaying(final File file) throws IOException;
  void cassetteStartRecording(final File file) throws IOException;
  void cassetteStop();
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
