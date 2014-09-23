package edu.ucla.library.mdxform;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.freelibrary.util.IOUtils;
import info.freelibrary.util.StringUtils;

@WebServlet(name = "DownloadServlet", urlPatterns = { "/download" })
public class DownloadServlet extends HttpServlet {

    private static final Logger LOGGER = LoggerFactory.getLogger(DownloadServlet.class.getCanonicalName());

    @Override
    protected void doGet(final HttpServletRequest aRequest, final HttpServletResponse aResponse)
            throws ServletException, IOException {
        final File dataDir = new File(getServletContext().getRealPath("data"));
        final String job = StringUtils.trimToNull(aRequest.getParameter("job"));

        if (job != null) {
            final File jobDir = new File(dataDir, job);

            if (jobDir.exists()) {
                if (new File(jobDir, TransformationServlet.STATUS_COMPLETED).exists()) {
                    final File zipFile = new File(jobDir, "mdxform-" + job + ".zip");

                    // Let's remove any old artifacts for each attempted download
                    if (zipFile.exists() && !zipFile.delete() && LOGGER.isErrorEnabled()) {
                        LOGGER.error("Unabled to delete pre-existing Zip file: {}", zipFile);
                    }

                    final FileOutputStream outStream = new FileOutputStream(zipFile);
                    final BufferedOutputStream bufferedStream = new BufferedOutputStream(outStream);
                    final ZipOutputStream zipStream = new ZipOutputStream(bufferedStream);
                    final String dirName = "mdxform-" + job + "-xml/";

                    zipStream.putNextEntry(new ZipEntry(dirName));

                    for (final File xmlFile : new File(jobDir, "xml").listFiles()) {
                        zipStream.putNextEntry(new ZipEntry(dirName + xmlFile.getName()));
                        IOUtils.copyStream(xmlFile, zipStream);
                        zipStream.closeEntry();
                    }

                    zipStream.close();

                    // Now, return our newly minted Zip file to the browser
                    final ServletOutputStream servletOutStream = aResponse.getOutputStream();
                    aResponse.setContentType("application/zip");
                    aResponse.setHeader("Content-Disposition", "attachment; filename=\"" + zipFile.getName() + "\"");

                    IOUtils.copyStream(zipFile, servletOutStream);
                    servletOutStream.flush();
                    servletOutStream.close();
                } else if (new File(jobDir, TransformationServlet.STATUS_FAILED).exists()) {
                    final PrintWriter writer = aResponse.getWriter();
                    aResponse.setContentType("text/html");
                    writer.write("<div>Sorry but the requested job failed to completed successfully.</div>");
                    writer.close();
                } else {
                    final PrintWriter writer = aResponse.getWriter();
                    aResponse.setContentType("text/html");
                    writer.write("<div>Please wait until the job has completed before downloading the results.</div>");
                    writer.close();
                }
            } else {
                aResponse.sendError(404, "Requested job no longer exists on the server");
            }
        } else {
            aResponse.sendError(400, "No job was submitted for download");
        }

    }
}
