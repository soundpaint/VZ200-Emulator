package emulator.vz200;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;

import emulator.z80.CPU;
import emulator.z80.WallClockProvider;

public class VZFileControl extends Box
{
  private static final long serialVersionUID = -7955899435606069032L;

  private final CPU.Memory memory;
  private final WallClockProvider wallClockProvider;
  private final VZFileChooser loadFileChooser, saveFileChooser;
  private final JButton btnLoad, btnSave;
  private File file;

  public VZFileControl(final CPU.Memory memory,
                       final WallClockProvider wallClockProvider)
  {
    super(BoxLayout.X_AXIS);
    if (memory == null) {
      throw new NullPointerException("memory");
    }
    this.memory = memory;
    if (wallClockProvider == null) {
      throw new NullPointerException("wallClockProvider");
    }
    this.wallClockProvider = wallClockProvider;
    setBorder(BorderFactory.createTitledBorder("VZ Files Load & Save"));
    loadFileChooser =
      new VZFileChooser("Load Data from External VZ File",
                        "Load VZ File");
    saveFileChooser =
      new VZFileChooser("Save Data to External VZ File",
                        "Save VZ File");
    btnLoad = new JButton("Load");
    btnLoad.setMnemonic(KeyEvent.VK_L);
    btnLoad.addActionListener((final ActionEvent event) -> { load(); });
    add(btnLoad);
    add(Box.createHorizontalStrut(5));
    btnSave = new JButton("Save");
    btnSave.setMnemonic(KeyEvent.VK_S);
    btnSave.addActionListener((final ActionEvent event) -> { save(); });
    add(btnSave);
    add(Box.createHorizontalGlue());
  }

  private static final byte[] VZ_MAGIC = {0x20, 0x20, 0x00, 0x00};

  private void load(final File file)
  {
    try {
      VZFile.load(file, memory, wallClockProvider);
    } catch (final IOException e) {
      final String message =
        "Failed loading VZ file " + file.getName() + ": " + e.getMessage();
      final String title = "Failed Loading VZ File";
      JOptionPane.showMessageDialog(this, message, title,
                                    JOptionPane.WARNING_MESSAGE);
    }
  }

  private void load()
  {
    final int option = loadFileChooser.showDialog(this, null);
    switch (option) {
    case JFileChooser.APPROVE_OPTION:
      file = loadFileChooser.getSelectedFile();
      load(file);
      break;
    case JFileChooser.CANCEL_OPTION:
      break;
    case JFileChooser.ERROR_OPTION:
      break;
    default:
      break;
    }
  }

  private void save(final File file)
  {
    try {
      final int startAddress = 0; // TODO
      final int length = 0; // TODO
      VZFile.saveBinary(startAddress, length, file, memory, wallClockProvider);
    } catch (final IOException e) {
      final String message =
        "Failed saving VZ file " + file.getName() + ": " + e.getMessage();
      final String title = "Failed Saving VZ File";
      JOptionPane.showMessageDialog(this, message, title,
                                    JOptionPane.WARNING_MESSAGE);
    }
  }

  private void save()
  {
    final int option = saveFileChooser.showDialog(this, null);
    switch (option) {
    case JFileChooser.APPROVE_OPTION:
      file = saveFileChooser.getSelectedFile();
      if (file.exists()) {
        final int choice =
          JOptionPane.showConfirmDialog(this,
                                        "Overwrite " + file.getName() + "?",
                                        "Confirm Overwrite",
                                        JOptionPane.YES_NO_OPTION,
                                        JOptionPane.QUESTION_MESSAGE);
        if (choice != JOptionPane.YES_OPTION) {
          return;
        }
      }
      save(file);
      break;
    case JFileChooser.CANCEL_OPTION:
      break;
    case JFileChooser.ERROR_OPTION:
      break;
    default:
      break;
    }
  }
}

/*
 * Local Variables:
 *   coding:utf-8
 *   mode:Java
 * End:
 */
