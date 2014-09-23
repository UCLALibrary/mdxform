
package edu.ucla.library.mdxform;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Calendar;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.IOUtils;
import info.freelibrary.util.StringUtils;

@WebServlet(name = "TransformationServlet", urlPatterns = { "/transform" })
@MultipartConfig
public class TransformationServlet extends HttpServlet {

    /**
     * <code>serialVersionUID</code> for the MetaXFormServlet.
     */
    private static final long serialVersionUID = -4703817112557628641L;

    public static final String STATUS_COMPLETED = "status.completed";

    public static final String STATUS_FAILED = "status.failed";

    private static final String XSLT_FILE_NAME = "xsltfile";

    private static final String ZIP_FILE_NAME = "zipfile";

    private static final String OUTPUT_DIR_NAME = "output";

    private static final String XML_DIR_NAME = "xml";

    private final Logger LOGGER = LoggerFactory.getLogger(TransformationServlet.class.getCanonicalName());

    @Override
    protected void doPost(final HttpServletRequest aRequest, final HttpServletResponse aResponse)
            throws ServletException, IOException {
        final String timestamp = Long.toString(Calendar.getInstance().getTimeInMillis());
        final File dataDir = new File(getServletContext().getRealPath("data"), timestamp);
        final Part xsltFilePart = aRequest.getPart(XSLT_FILE_NAME);
        final Part zipFilePart = aRequest.getPart(ZIP_FILE_NAME);
        final String xsltFileName = getFullFileName(xsltFilePart);
        final String zipFileName = getFullFileName(zipFilePart);
        final File outputDir = new File(dataDir, OUTPUT_DIR_NAME);

        if (!dataDir.exists() && !dataDir.mkdirs()) {
            aResponse.sendError(500, "Unable to create data directory: " + dataDir);
            return;
        }

        if (!outputDir.exists() && !outputDir.mkdirs()) {
            FileUtils.delete(dataDir);
            aResponse.sendError(500, "Unable to create output directory: " + outputDir);
            return;
        }

        if (xsltFileName == null) {
            FileUtils.delete(dataDir);
            aResponse.sendError(400, "XSLT file not uploaded");
            return;
        }

        if (zipFileName == null) {
            FileUtils.delete(dataDir);
            aResponse.sendError(400, "Zipped metadata files not uploaded");
            return;
        }

        final File xsltFile = writeFile(xsltFilePart, new File(dataDir, xsltFileName));
        final File zipFile = writeFile(zipFilePart, new File(dataDir, zipFileName));

        if (LOGGER.isInfoEnabled()) {
            LOGGER.info("XSLT file '{}' uploaded", xsltFileName);
            LOGGER.info("Zipped metadata files uploaded in '{}'", zipFileName);
        }

        unzipMetadataFiles(zipFile);

        // We can clean up the Zip file once we've successfully unpacked it
        zipFile.delete();

        // Fire off our asynchronous transformation process
        new Thread(new TransformationEngine(timestamp, dataDir, xsltFile)).start();

        aResponse.sendRedirect("status?added=" + timestamp);
    }

    private String getFullFileName(final Part part) {
        final String partHeader = part.getHeader("content-disposition");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Part Header = {}", partHeader);
        }

        for (final String content : part.getHeader("content-disposition").split(";")) {
            if (content.trim().startsWith("filename")) {
                return StringUtils.trimToNull(content.substring(content.indexOf('=') + 1).trim().replace("\"", ""));
            }
        }

        return null;
    }

    private String cleanFileName(final String aFileName) {
        if (aFileName.contains("/")) {
            return aFileName.substring(aFileName.lastIndexOf('/') + 1);
        } else {
            return aFileName;
        }
    }

    private File writeFile(final Part aFromPart, final File aToFile) throws ServletException {
        OutputStream outStream = null;
        InputStream inStream = null;

        try {
            outStream = new FileOutputStream(aToFile);
            inStream = aFromPart.getInputStream();
            IOUtils.copyStream(inStream, outStream);
        } catch (final Exception details) {
            if (LOGGER.isErrorEnabled()) {
                LOGGER.error("Problems during file upload: {}", details.getMessage());
            }

            throw new ServletException(details);
        } finally {
            IOUtils.closeQuietly(outStream);
            IOUtils.closeQuietly(inStream);
        }

        return aToFile;
    }

    private void unzipMetadataFiles(final File aZipFile) throws ServletException {
        final File parentDir = aZipFile.getParentFile();
        final File xmlDir = new File(parentDir, XML_DIR_NAME);

        if (!xmlDir.mkdirs()) {
            throw new ServletException("Could not create 'xml' data directory: " + xmlDir);
        }

        ZipInputStream zipStream = null;
        ZipEntry zipEntry;

        try {
            zipStream = new ZipInputStream(new FileInputStream(aZipFile));
            zipEntry = zipStream.getNextEntry();

            while (zipEntry != null) {
                final String fileName = cleanFileName(zipEntry.getName());

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Unpacking {} from metadata Zip file", fileName);
                }

                if (fileName.endsWith(".xml") && !fileName.startsWith(".")) {
                    final File xmlFile = new File(xmlDir, fileName);
                    final FileOutputStream xmlStream = new FileOutputStream(xmlFile);

                    IOUtils.copyStream(zipStream, xmlStream);
                    xmlStream.close();
                } else if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Found ZipEntry that isn't an XML file");
                }

                zipEntry = zipStream.getNextEntry();
            }

        } catch (final IOException details) {
            throw new ServletException(details);
        } finally {
            if (zipStream != null) {
                try {
                    zipStream.closeEntry();
                } catch (final IOException details) {
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error(details.getMessage(), details);
                    }
                }
            }

            IOUtils.closeQuietly(zipStream);
        }
    }

    private class TransformationEngine implements Runnable {

        private final File myOutputDir;

        private final String myJobID;

        private final File myXMLDir;

        private final File myXSLT;

        private final Transformer myTransformer;

        private TransformationEngine(final String aJobID, final File aDataDir, final File aXSLT)
                throws ServletException {
            myOutputDir = new File(aDataDir, OUTPUT_DIR_NAME);
            myXMLDir = new File(aDataDir, XML_DIR_NAME);
            myJobID = aJobID;
            myXSLT = aXSLT;

            // Configure our XSLT engine for use...
            try {
                final TransformerFactory transformerFactory = TransformerFactory.newInstance();
                myTransformer = transformerFactory.newTransformer(new StreamSource(aXSLT));
            } catch (final TransformerConfigurationException details) {
                throw new ServletException("Transformation engine is misconfigured");
            }
        }

        @Override
        public void run() {
            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Starting transformer thread for {}", myJobID);
            }

            for (final File xmlFile : myXMLDir.listFiles()) {
                final File outputFile = new File(myOutputDir, xmlFile.getName());

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Transforming {} into {} using {}", xmlFile, outputFile, myXSLT);
                }

                try {
                    myTransformer.transform(new StreamSource(xmlFile), new StreamResult(outputFile));
                } catch (final TransformerException details) {
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error("Failed to transform: {} using {}", xmlFile, myXSLT, details);
                    }
                }
            }

            if (LOGGER.isInfoEnabled()) {
                LOGGER.info("Transformation for {} now complete!", myJobID);
            }

            if (myXMLDir.listFiles().length == myOutputDir.listFiles().length) {
                final File statusFile = new File(myXMLDir.getParent(), STATUS_COMPLETED);

                try {
                    if (statusFile.createNewFile()) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Created status file for {}", myJobID);
                        }
                    }
                } catch (final IOException details) {
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error("Failed to create status file for {}", myJobID, details);
                    }
                }
            } else {
                final File statusFile = new File(myXMLDir.getParent(), STATUS_FAILED);

                try {
                    if (statusFile.createNewFile()) {
                        if (LOGGER.isDebugEnabled()) {
                            LOGGER.debug("Created status file for {}", myJobID);
                        }
                    }
                } catch (final IOException details) {
                    if (LOGGER.isErrorEnabled()) {
                        LOGGER.error("Failed to create status file for {}", myJobID, details);
                    }
                }
            }
        }
    }
}
