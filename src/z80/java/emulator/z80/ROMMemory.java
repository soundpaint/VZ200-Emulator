// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

import java.io.DataInputStream;
import java.io.InputStream;
import java.io.IOException;

/**
 * Default implementation for ROM Memory.
 */
public class ROMMemory extends RAMMemory
{
  private ROMMemory() {}

  public ROMMemory(int[] rom, int minAddr) {
    this.ram = cloneByteArray(rom);
    this.minAddr = minAddr;
    this.maxAddr = minAddr + ram.length - 1;
  }

  public ROMMemory(Class<? extends Object> baseClass, String resourceName, int minAddr, int size)
    throws IOException
  {
    this(loadRom(baseClass, resourceName, size), minAddr);
  }

  private static int[] loadRom(Class<? extends Object> baseClass, String resourceName, int size)
    throws IOException
  {
    byte[] romBytes = new byte[size];
    InputStream resource = baseClass.getResourceAsStream(resourceName);
    if (resource == null)
      throw new IOException("resource '" + resourceName + "' not found");
    DataInputStream is =
      new DataInputStream(resource);
    is.readFully(romBytes);
    if (is.read() != -1)
      throw new IOException("EOF expected: " + resourceName +
			    " file too long");
    is.close();
    int[] rom = new int[size];
    for (int i = 0; i < size; i++)
      rom[i] = romBytes[i] & 0xff;
    return rom;
  }

  public void writeByte(int address, int value) {}
  public void writeShort(int address, int value) {}
  public void writeInt(int address, int value) {}

  private int[] cloneByteArray(int[] array) {
    int[] clone = new int[array.length];
    for (int i = 0; i < array.length; i++)
      clone[i] = array[i]; // TODO: use some kind of System.arrayCopy for int[]
    return clone;
  }

  public int[] getByteArray() {
    return cloneByteArray(ram);
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
