
package edu.ucla.library.mdxform;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

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

import info.freelibrary.util.StringUtils;

@WebServlet(name = "LogViewServlet", urlPatterns = { "/logs" })
public class LogViewServlet extends HttpServlet {

    /**
     * <code>serialVersionUID</code> for LogViewServlet.
     */
    private static final long serialVersionUID = 9172181160553581555L;

    @Override
    protected void doGet(final HttpServletRequest aRequest, final HttpServletResponse aResponse)
            throws ServletException, IOException {
        // Go ahead and tell the browser what we'll be returning
        aResponse.setContentType("text/html;charset=UTF-8");

        final ServletOutputStream outStream = aResponse.getOutputStream();
        final Serializer serializer = new ViewerSerializer(outStream);
        final ServletContext context = getServletContext();
        final String level = StringUtils.trimToNull(aRequest.getParameter("level"));
        final String lines = StringUtils.trimTo(aRequest.getParameter("lines"), "");

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

        final Element mainDiv = (Element) nodes.get(0);
        final Element script = new Element("script");
        final Attribute scriptType = new Attribute("type", "text/javascript");
        final Attribute scriptLang = new Attribute("language", "javascript");
        final Element wellDiv = new Element("div");
        final Attribute wellClass = new Attribute("class", "well");
        final Element h2 = new Element("h2");
        final Element wellDivP1 = new Element("p");
        final Element br = new Element("br");

        h2.appendChild("Logs");
        wellDiv.addAttribute(wellClass);
        wellDiv.appendChild(h2);
        wellDiv.appendChild(wellDivP1);
        wellDivP1.appendChild("A very basic log browser to help determine what went wrong.");
        mainDiv.appendChild(wellDiv);

        // This could be a little more dynamic, but what's here will work for now
        script.addAttribute(scriptType);
        script.addAttribute(scriptLang);
        script.appendChild("var xmlhttp = new XMLHttpRequest(); ");
        script.appendChild("xmlhttp.onreadystatechange = function() { ");
        script.appendChild(" if (xmlhttp.readyState == 4) { ");
        script.appendChild("  if (xmlhttp.status == 200) { ");
        script.appendChild("   var responseDiv = document.createElement('div'); ");
        script.appendChild("   responseDiv.innerHTML = xmlhttp.responseText; ");
        script.appendChild("   /* responseDiv.setAttribute('style', 'overflow-y: scroll'); */ ");
        script.appendChild("   document.getElementById('main-container').appendChild(responseDiv); ");
        script.appendChild("  } else { ");
        script.appendChild("   var responseDiv = document.createElement('div'); ");
        script.appendChild("   responseDiv.innerHTML = 'Did not receive any logs'; ");
        script.appendChild("   document.getElementById('main-container').appendChild(responseDiv); ");
        script.appendChild("  } ");
        script.appendChild(" } ");
        script.appendChild("}; ");
        script.appendChild("xmlhttp.open('GET', '/mdxform/logstream?lines=" + lines + "', false); ");
        script.appendChild("xmlhttp.send(); ");
        mainDiv.appendChild(script);
        mainDiv.appendChild(br);

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
