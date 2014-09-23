
package edu.ucla.library.mdxform;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;

/**
 * Function to read a file in reverse order (<a
 * href="http://stackoverflow.com/questions/8664705/how-to-read-file-from-end-to-start-in-reverse-order-in-java">From
 * StackOverflow</a>).
 */
public class ReverseLineInputStream extends InputStream {

    private final RandomAccessFile myFile;

    private long myCurrentLineStart = -1;

    private long myCurrentLineEnd = -1;

    private long myCurrentPos = -1;

    private long myLastPosInFile = -1;

    public ReverseLineInputStream(final File aFile) throws FileNotFoundException {
        myFile = new RandomAccessFile(aFile, "r");

        myCurrentLineStart = aFile.length();
        myCurrentLineEnd = aFile.length();
        myLastPosInFile = aFile.length() - 1;
        myCurrentPos = myCurrentLineEnd;
    }

    public void findPrevLine() throws IOException {
        myCurrentLineEnd = myCurrentLineStart;

        // There are no more lines, since we are at the beginning of the file and no lines
        if (myCurrentLineEnd == 0) {
            myCurrentLineEnd = -1;
            myCurrentLineStart = -1;
            myCurrentPos = -1;

            return;
        }

        long filePointer = myCurrentLineStart - 1;

        while (true) {
            filePointer--;

            // we are at start of file so this is the first line in the file.
            if (filePointer < 0) {
                break;
            }

            myFile.seek(filePointer);

            // We ignore last LF in file. search back to find the previous LF.
            if (myFile.readByte() == 0xA && filePointer != myLastPosInFile) {
                break;
            }
        }

        // We want to start at pointer +1 so we are after the LF we found or at 0 the start of the file
        myCurrentLineStart = filePointer + 1;
        myCurrentPos = myCurrentLineStart;
    }

    @Override
    public int read() throws IOException {
        if (myCurrentPos < myCurrentLineEnd) {
            myFile.seek(myCurrentPos++);
            return myFile.readByte();
        } else if (myCurrentPos < 0) {
            return -1;
        } else {
            findPrevLine();
            return read();
        }
    }

}