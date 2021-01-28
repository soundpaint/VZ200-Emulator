package emulator.vz200;

import java.io.File;

public interface CassetteInputSampler extends ProgressProvider
{
  static final short VALUE_LO = -32768;
  static final short VALUE_HI = 32767;

  short getValue(final long wallClockTime);
  void stop();
  boolean isStopped();
  File getFile();
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
