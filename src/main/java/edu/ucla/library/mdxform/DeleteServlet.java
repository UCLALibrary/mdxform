
package edu.ucla.library.mdxform;

import java.io.File;
import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import info.freelibrary.util.FileUtils;
import info.freelibrary.util.StringUtils;

@WebServlet(name = "DeleteServlet", urlPatterns = { "/delete" })
public class DeleteServlet extends HttpServlet {

    /**
     * <code>serialVersionUID</code> for the DeleteServlet
     */
    private static final long serialVersionUID = -1421118877970030884L;

    @Override
    protected void doGet(final HttpServletRequest aRequest, final HttpServletResponse aResponse)
            throws ServletException, IOException {
        final File dataDir = new File(getServletContext().getRealPath("data"));
        final String job = StringUtils.trimToNull(aRequest.getParameter("job"));

        if (job != null) {
            final File jobDir = new File(dataDir, job);

            if (jobDir.exists()) {
                if (!FileUtils.delete(jobDir)) {
                    aResponse.sendError(500, "Failed to delete requested directory");
                }
            }
        } else {
            aResponse.sendError(400, "No job was submitted for deletion");
        }

        aResponse.sendRedirect("status?deleted=" + job);
    }

}
