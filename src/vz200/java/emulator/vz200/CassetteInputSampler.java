package emulator.vz200;

public interface CassetteInputSampler
{
  short getValue(final long wallClockTime);
  boolean isStopped();
  String getFileName();
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
