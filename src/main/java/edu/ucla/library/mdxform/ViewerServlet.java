
package edu.ucla.library.mdxform;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nu.xom.Attribute;
import nu.xom.Builder;
import nu.xom.Document;
import nu.xom.Element;
import nu.xom.Nodes;
import nu.xom.ParsingException;
import nu.xom.Serializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import info.freelibrary.util.DirFileFilter;
import info.freelibrary.util.StringUtils;

@WebServlet(name = "ViewerServlet", urlPatterns = { "/status" })
public class ViewerServlet extends HttpServlet {

    /**
     * <code>serialVersionUID</code> for ViewerServlet.
     */
    private static final long serialVersionUID = -4107065366323271507L;

    private static final Logger LOGGER = LoggerFactory.getLogger(ViewerServlet.class.getCanonicalName());

    @Override
    protected void doGet(final HttpServletRequest aRequest, final HttpServletResponse aResponse)
            throws ServletException, IOException {
        // Go ahead and tell the browser what we'll be returning
        aResponse.setContentType("text/html;charset=UTF-8");

        final ServletContext context = getServletContext();
        final File dataDir = new File(context.getRealPath("data"));
        final DirFileFilter dirFilter = new DirFileFilter();
        final ServletOutputStream outStream = aResponse.getOutputStream();
        final Serializer serializer = new ViewerSerializer(outStream);
        final File[] dirs = dataDir.listFiles(dirFilter);
        final String jobCountLabel = "(" + dirs.length + (dirs.length != 1 ? " jobs)" : " job)");
        final String titleString = "Transformation Processing Backlog " + jobCountLabel;
        final SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
        final String added = StringUtils.trimToNull(aRequest.getParameter("added"));
        final String deleted = StringUtils.trimToNull(aRequest.getParameter("deleted"));

        // Yes, yes... a templating framework would make the below much easier!
        final Builder bob = new Builder();
        final Document doc;

        try {
            doc = bob.build(new File(context.getRealPath("templates/wrapper.html")));
        } catch (final ParsingException details) {
            throw new ServletException("There is a problem with this page's HTML template", details);
        }

        final Nodes nodes = doc.query("//div[@id='main-container']");

        if (nodes.size() <= 0) {
            throw new ServletException("Couldn't load page because the HTML template is misconfigured");
        }

        final Element title = new Element("title");

        final Element mainDiv = (Element) nodes.get(0);
        title.appendChild(titleString);

        final Element h2 = new Element("h2");
        h2.appendChild(titleString);

        final Element wellDiv = new Element("div");
        final Attribute wellClass = new Attribute("class", "well");
        final Element table = new Element("table");
        final Element nameLabel = new Element("th");
        final Element lastModLabel = new Element("th");
        final Element statusLabel = new Element("th");
        final Element actionLabel = new Element("th");
        final Element labels = new Element("tr");
        final Element wellDivP1 = new Element("p");
        final Element wellDivP2 = new Element("p");

        wellDiv.addAttribute(wellClass);
        wellDiv.appendChild(h2);
        wellDiv.appendChild(wellDivP1);
        wellDiv.appendChild(wellDivP2);
        wellDivP1.appendChild("Jobs live here until they are deleted. ");
        wellDivP1.appendChild("Click 'Name' to view the job's files or 'Download' to get the transformed metadata. ");
        wellDivP1.appendChild("A job's 'Status' must be 'Completed' before it can be downloaded.");
        wellDivP2.appendChild("'Delete' removes all the job's files from the server's file system.");
        mainDiv.appendChild(wellDiv);
        mainDiv.appendChild(table);
        nameLabel.appendChild("Name (and kickoff time)");
        lastModLabel.appendChild("Last Modified");
        statusLabel.appendChild("Status");
        actionLabel.appendChild("Action");

        labels.appendChild(nameLabel);
        labels.appendChild(lastModLabel);
        labels.appendChild(statusLabel);
        labels.appendChild(actionLabel);
        table.appendChild(labels);

        // Sorting alpha-numerically should be fine for our longs
        Arrays.sort(dirs);

        // Let's put the most recent on top, though
        Collections.reverse(Arrays.asList(dirs));

        for (final File file : dirs) {
            final String jobName = file.getName();
            final Element tr = new Element("tr");
            final Element td1 = new Element("td");
            final Element td2 = new Element("td");
            final Element td3 = new Element("td");
            final Element td4 = new Element("td");
            final Element dirLink = new Element("a");
            final Attribute dirHref = new Attribute("href", "data/" + jobName);
            final Element deleteLink = new Element("a");
            final Element downloadLink = new Element("a");
            final Attribute deleteHref = new Attribute("href", "delete?job=" + jobName);
            final Attribute downloadHref = new Attribute("href", "download?job=" + jobName);

            dirLink.addAttribute(dirHref);
            dirLink.appendChild(formatter.format(new Date(Long.parseLong(jobName))));
            td1.appendChild(dirLink);
            td2.appendChild(formatter.format(new Date(file.lastModified())));

            if (added != null && added.equals(jobName)) {
                td3.appendChild("Added");
            } else if (new File(file, TransformationServlet.STATUS_COMPLETED).exists()) {
                td3.appendChild("Completed");
            } else if (new File(file, TransformationServlet.STATUS_FAILED).exists()) {
                td3.appendChild("Failed");
            } else {
                td3.appendChild("In Process");
            }

            deleteLink.addAttribute(deleteHref);
            deleteLink.appendChild("Delete");
            downloadLink.addAttribute(downloadHref);
            downloadLink.appendChild("Download");
            td4.appendChild(deleteLink);
            td4.appendChild(" | ");
            td4.appendChild(downloadLink);

            tr.appendChild(td1);
            tr.appendChild(td2);
            tr.appendChild(td3);
            tr.appendChild(td4);
            table.appendChild(tr);
        }

        if (deleted != null) {
            final Element deletedDiv = new Element("div");
            final Element jobSpan = new Element("span");
            final Attribute idAttribute = new Attribute("id", "deleted");
            final Attribute classAttribute = new Attribute("class", "bold");
            deletedDiv.addAttribute(idAttribute);
            jobSpan.addAttribute(classAttribute);
            jobSpan.appendChild(formatter.format(new Date(Long.parseLong(deleted))));
            deletedDiv.appendChild("Update: ");
            deletedDiv.appendChild(jobSpan);
            deletedDiv.appendChild(" deleted from the file system");
            mainDiv.appendChild(deletedDiv);
        }

        final Element refreshButton = new Element("form");
        final Attribute refreshId = new Attribute("id", "refresh");
        final Attribute refreshAction = new Attribute("action", "status");
        final Element refreshSubmit = new Element("input");
        final Attribute submitInput = new Attribute("type", "submit");
        final Attribute submitValue = new Attribute("value", "Refresh");
        refreshButton.addAttribute(refreshId);
        refreshButton.addAttribute(refreshAction);
        refreshSubmit.addAttribute(submitInput);
        refreshSubmit.addAttribute(submitValue);
        refreshButton.appendChild(refreshSubmit);
        mainDiv.appendChild(refreshButton);

        serializer.setIndent(2);
        serializer.write(doc);
        serializer.flush();
        outStream.close();
    }

    class ViewerSerializer extends Serializer {

        private static final String DEFAULT_ENCODING = "UTF-8";

        ViewerSerializer(final ServletOutputStream aOutputStream) throws UnsupportedEncodingException {
            super(aOutputStream, DEFAULT_ENCODING);
        }

        @Override
        protected void writeXMLDeclaration() {
        }

    }

}
