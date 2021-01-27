package emulator.vz200;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileFilter;
import javax.swing.filechooser.FileNameExtensionFilter;

public class CassetteFileChooser extends JFileChooser
{
  private static final long serialVersionUID = 35066383232434466L;
  private static final FileFilter rawFileFilter =
    new FileNameExtensionFilter("Raw Audio Files, 44.1kHz, 16 bit PCM mono (.raw)",
                                "raw");
  private static final FileFilter wavFileFilter =
    new FileNameExtensionFilter("Wave Audio Files, 44.1kHz, 16 bit PCM mono (.wav)",
                                "wav");
  private static final FileFilter vzFileFilter =
    new FileNameExtensionFilter("VZ files (.vz)",
                                "vz");

  public static final AudioFileFormat.Type DEFAULT_FILE_TYPE =
    AudioFileFormat.Type.WAVE;
  public static final float DEFAULT_SAMPLE_RATE = 44100.0f;
  public static final int DEFAULT_BYTES_PER_FRAME = 2;
  public static final AudioFormat TOGGLE_BIT_AUDIO =
    new AudioFormat(DEFAULT_SAMPLE_RATE, 16, 1, true, false);

  private CassetteFileChooser()
  {
    throw new UnsupportedOperationException("unsupported empty constructor");
  }

  public CassetteFileChooser(final String dialogTitle,
                             final String approveButtonText,
                             final boolean acceptRawFiles,
                             final boolean acceptWavFiles,
                             final boolean acceptVzFiles)
  {
    setDialogTitle(dialogTitle);
    setDialogType(CassetteFileChooser.CUSTOM_DIALOG);
    setApproveButtonText(approveButtonText);
    setMultiSelectionEnabled(false);
    if (acceptRawFiles) {
      addChoosableFileFilter(rawFileFilter);
      setFileFilter(rawFileFilter);
    }
    if (acceptWavFiles) {
      addChoosableFileFilter(wavFileFilter);
      setFileFilter(wavFileFilter);
    }
    if (acceptVzFiles) {
      addChoosableFileFilter(vzFileFilter);
      setFileFilter(vzFileFilter);
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
