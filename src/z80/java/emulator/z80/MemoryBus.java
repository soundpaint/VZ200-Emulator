// TODO: move this class into new package 'emulator.cpu'.
package emulator.z80;

import java.util.ArrayList;
import java.util.List;

/**
 * Combines possibly multiple bus readers and writers to a unified
 * memory interface.  Hence, an implementor can implement RAM, ROM,
 * memory-mapped IO etc. as separate implementations of the Memory
 * interface and register them as listeners of this single Memory
 * interface implementation.
 */
public class MemoryBus implements CPU.Memory {
  /*
   * A device connected to the memory bus that is listening to data
   * that the CPU writes to the bus.
   */
  public interface Reader {
    public void writeByte(int address, int value);
    public void writeShort(int address, int value);
  }

  /*
   * A device connected to the memory bus that provides data for the
   * CPU to read from the bus.
   */
  public interface Writer {
    /**
     * The default value that a device must issue onto the bus
     * if it does not feel addressed.
     */
    public static final int BYTE_UNDEFINED = 0xff;

    public int readByte(int address);
    public int readShort(int address);
  }

  public static MemoryBus createRAMMemoryBus(int baseAddress, int size)
  {
    MemoryBus memoryBus = new MemoryBus();
    RAMMemory ramMemory = new RAMMemory(baseAddress, size);
    memoryBus.addReader(ramMemory);
    memoryBus.addWriter(ramMemory);
    return memoryBus;
  }

  private List<Reader> readers;
  private List<Writer> writers;

  public MemoryBus() {
    readers = new ArrayList<Reader>();
    writers = new ArrayList<Writer>();
  }

  public void addReader(Reader reader) {
    if (reader == null)
      throw new NullPointerException("reader");
    readers.add(reader);
  }

  public void addWriter(Writer writer) {
    if (writer == null)
      throw new NullPointerException("writer");
    writers.add(writer);
  }

  public int readByte(int address) {
    int result = 0xff;
    for (Writer writer : writers) {
      result &= writer.readByte(address);
    }
    return result;
  }

  public int readShort(int address) {
    int result = 0xffff;
    for (Writer writer : writers) {
      result &= writer.readShort(address);
    }
    return result;
  }

  public void writeByte(int address, int value) {
    for (Reader reader : readers) {
      reader.writeByte(address, value);
    }
  }

  public void writeShort(int address, int value) {
    for (Reader reader : readers) {
      reader.writeShort(address, value);
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
