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
  public interface BusReader {
    public void writeByte(int address, int value, long wallClockTime);
    public void writeShort(int address, int value, long wallClockTime);
  }

  /*
   * A device connected to the memory bus that provides data for the
   * CPU to read from the bus.
   */
  public interface BusWriter {
    /**
     * The default value that a device must issue onto the bus
     * if it does not feel addressed.
     */
    public static final int BYTE_UNDEFINED = 0xff;

    public int readByte(int address, long wallClockTime);
    public int readShort(int address, long wallClockTime);
  }

  public static MemoryBus createRAMMemoryBus(int baseAddress, int size)
  {
    MemoryBus memoryBus = new MemoryBus();
    RAMMemory ramMemory = new RAMMemory(baseAddress, size);
    memoryBus.addReader(ramMemory);
    memoryBus.addWriter(ramMemory);
    return memoryBus;
  }

  private List<BusReader> readers;
  private List<BusWriter> writers;

  public MemoryBus() {
    readers = new ArrayList<BusReader>();
    writers = new ArrayList<BusWriter>();
  }

  public void addReader(BusReader reader) {
    if (reader == null)
      throw new NullPointerException("reader");
    readers.add(reader);
  }

  public void addWriter(BusWriter writer) {
    if (writer == null)
      throw new NullPointerException("writer");
    writers.add(writer);
  }

  public int readByte(int address, long wallClockTime) {
    int result = 0xff;
    for (BusWriter writer : writers) {
      result &= writer.readByte(address, wallClockTime);
    }
    return result;
  }

  public int readShort(int address, long wallClockTime) {
    int result = 0xffff;
    for (BusWriter writer : writers) {
      result &= writer.readShort(address, wallClockTime);
    }
    return result;
  }

  public void writeByte(int address, int value, long wallClockTime) {
    for (BusReader reader : readers) {
      reader.writeByte(address, value, wallClockTime);
    }
  }

  public void writeShort(int address, int value, long wallClockTime) {
    for (BusReader reader : readers) {
      reader.writeShort(address, value, wallClockTime);
    }
  }
}

/*
  Local Variables:
    coding:utf-8
    mode:Java
    End:
*/
