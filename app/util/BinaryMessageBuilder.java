package util;

import static util.Constants.DOUBLE_BYTES;

public class BinaryMessageBuilder {

    // initialized number of records
    static int INIT_CAPACITY = 2000;

    /**
     * ---- header ----
     * | HEADER_SIZE | ...
     * ---- binary data payload ----
     * lat              lng
     * | DOUBLE_BYTES | DOUBLE_BYTES | ...(repeat)...
     */
    byte[] buffer;
    int capacity;
    int count;
    public BinaryMessageBuilder() {
        capacity = INIT_CAPACITY;
        buffer = new byte[Constants.HEADER_SIZE + (DOUBLE_BYTES + DOUBLE_BYTES) * INIT_CAPACITY];
        count = 0;
    }

    public void add(double lng, double lat) {
        // buffer is full
        if (count == capacity - 1) {
            // double the size
            byte[] newBuffer = new byte[Constants.HEADER_SIZE + (DOUBLE_BYTES + DOUBLE_BYTES) * capacity * 2];
            // copy to new buffer
            System.arraycopy(buffer, 0, newBuffer, 0, Constants.HEADER_SIZE + (DOUBLE_BYTES + DOUBLE_BYTES) * capacity);
            capacity *= 2;
            buffer = newBuffer;
        }
        // move offset
        int j = Constants.HEADER_SIZE + count * (DOUBLE_BYTES + DOUBLE_BYTES);
        // write latitude
        long y = Double.doubleToRawLongBits(lat);
        buffer[j+0] = (byte) ((y >> 56) & 0xff);
        buffer[j+1] = (byte) ((y >> 48) & 0xff);
        buffer[j+2] = (byte) ((y >> 40) & 0xff);
        buffer[j+3] = (byte) ((y >> 32) & 0xff);
        buffer[j+4] = (byte) ((y >> 24) & 0xff);
        buffer[j+5] = (byte) ((y >> 16) & 0xff);
        buffer[j+6] = (byte) ((y >>  8) & 0xff);
        buffer[j+7] = (byte) ((y >>  0) & 0xff);
        // move offset
        j = j + DOUBLE_BYTES;
        // write longitude
        long x = Double.doubleToRawLongBits(lng);
        buffer[j+0] = (byte) ((x >> 56) & 0xff);
        buffer[j+1] = (byte) ((x >> 48) & 0xff);
        buffer[j+2] = (byte) ((x >> 40) & 0xff);
        buffer[j+3] = (byte) ((x >> 32) & 0xff);
        buffer[j+4] = (byte) ((x >> 24) & 0xff);
        buffer[j+5] = (byte) ((x >> 16) & 0xff);
        buffer[j+6] = (byte) ((x >>  8) & 0xff);
        buffer[j+7] = (byte) ((x >>  0) & 0xff);

        // move pos
        count++;
    }

    public byte[] getBuffer() {
        // shrink buffer to exact the size of data payload
        byte[] newBuffer = new byte[Constants.HEADER_SIZE + (DOUBLE_BYTES + DOUBLE_BYTES) * count];
        // copy to new buffer
        System.arraycopy(buffer, 0, newBuffer, 0, Constants.HEADER_SIZE + (DOUBLE_BYTES + DOUBLE_BYTES) * count);
        buffer = newBuffer;
        return buffer;
    }
}
