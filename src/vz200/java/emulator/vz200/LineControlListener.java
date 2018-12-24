package emulator.vz200;

public interface LineControlListener
{
  void lineChanged(final SourceDataLineChangeEvent event);
  void volumeChanged(final double volume);
  void mutedChanged(final boolean muted);
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
