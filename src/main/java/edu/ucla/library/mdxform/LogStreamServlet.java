
package edu.ucla.library.mdxform;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;

import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import nu.xom.Attribute;
import nu.xom.Element;
import nu.xom.Serializer;

import info.freelibrary.util.StringUtils;

@WebServlet(name = "LogStreamServlet", urlPatterns = { "/logstream" })
public class LogStreamServlet extends HttpServlet {

    // This gets converted to an integer if it needs to be used
    private static final String MAX_LINE_COUNT = "500";

    @Override
    protected void doGet(final HttpServletRequest aRequest, final HttpServletResponse aResponse)
            throws ServletException, IOException {
        final ServletContext context = getServletContext();
        final ServletOutputStream outStream = aResponse.getOutputStream();
        final File logFile = new File(context.getRealPath("WEB-INF/mdxform.error.log"));
        final BufferedReader reader = new BufferedReader(new InputStreamReader(new ReverseLineInputStream(logFile)));
        final String maxCountValue = StringUtils.trimTo(aRequest.getParameter("lines"), MAX_LINE_COUNT);

        int lineCount = 0;
        int maxLineCount;
        String line;

        try {
            maxLineCount = Integer.parseInt(maxCountValue);
        } catch (final NumberFormatException details) {
            maxLineCount = Integer.parseInt(MAX_LINE_COUNT);
        }

        while ((line = reader.readLine()) != null) {
            final String[] parts = line.split(" ");

            final Element div = new Element("div");
            final Element timeSpan = new Element("span");
            final Element threadSpan = new Element("span");
            final Element levelSpan = new Element("span");
            final Element classSpan = new Element("span");
            final Element messageSpan = new Element("span");

            div.addAttribute(new Attribute("id", "logEntry"));

            timeSpan.addAttribute(new Attribute("id", "time"));
            timeSpan.appendChild(parts[0] + " ");

            threadSpan.addAttribute(new Attribute("id", "thread"));
            threadSpan.appendChild(parts[1] + " ");

            levelSpan.addAttribute(new Attribute("id", "level"));
            levelSpan.appendChild(parts[2] + " ");

            classSpan.addAttribute(new Attribute("id", "class"));
            classSpan.appendChild(parts[3] + " ");

            messageSpan.addAttribute(new Attribute("id", "message"));

            for (int index = 4; index < parts.length; index++) {
                messageSpan.appendChild(parts[index] + " ");
            }

            div.appendChild(timeSpan);
            div.appendChild(threadSpan);
            div.appendChild(levelSpan);
            div.appendChild(classSpan);
            div.appendChild(messageSpan);

            outStream.println(div.toXML());
            outStream.flush();

            if (++lineCount >= maxLineCount) {
                break;
            }
        }

        final Element refreshButton = new Element("form");
        final Element lineCountInput = new Element("input");
        final Element refreshSubmit = new Element("input");
        final Element brTemplate = new Element("br");

        refreshButton.addAttribute(new Attribute("id", "refresh"));
        refreshButton.addAttribute(new Attribute("action", "logs"));
        refreshButton.addAttribute(new Attribute("method", "get"));
        refreshSubmit.addAttribute(new Attribute("type", "submit"));
        refreshSubmit.addAttribute(new Attribute("value", "Refresh"));
        lineCountInput.addAttribute(new Attribute("type", "text"));
        lineCountInput.addAttribute(new Attribute("name", "lines"));

        try {
            Integer.parseInt(maxCountValue);
            lineCountInput.addAttribute(new Attribute("value", maxCountValue));
        } catch (final NumberFormatException details) {
            // We don't have to do anything if the user has put in a bad value
        }

        lineCountInput.addAttribute(new Attribute("size", "4"));

        refreshButton.appendChild("Desired number of events: ");
        refreshButton.appendChild(lineCountInput);
        refreshButton.appendChild(brTemplate.copy());
        refreshButton.appendChild(brTemplate.copy());
        refreshButton.appendChild(refreshSubmit);

        outStream.println(refreshButton.toXML());
        outStream.flush();
        outStream.close();

        reader.close();
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
