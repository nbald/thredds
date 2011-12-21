/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package thredds.servlet;

import thredds.catalog.*;
import ucar.nc2.constants.FeatureType;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

/**
 * Database of triples describing the datasets within a dataset.
 * Currently only represents the singleton static dataset.
 *
 * @author Dennis Heimbigner
 */

public class Database
{

    //////////////////////////////////////////////////
    // Internal class representing a simplified dataset tree

    static public class Node implements Comparable
    {
        // Define the captured data for each dataset
        // (currently small subset of what is available)
        // Make fields externally accessible for now to
        // bypass need for set/get
        Node parent = null;
        String name = null;
        String id = null;
        CollectionType collectionType = null;
        FeatureType dataType = null;
        DataFormatType dataFormatType = null;
        List<ThreddsMetadata.Variables> variables;
        List<Node> datasets = new ArrayList<Node>();
        InvDataset source = null;
        boolean isroot = false;
        
        public Node() {this(false);}

        public Node(boolean isroot) {this.isroot = isroot;}

        // Comparable Interface
        public boolean equals(Object o)
        {
            if(o == null || !(o instanceof Node)) return false;
            return (compareTo((Node)o) == 0);
        }

        // Comparison is based solely on the id
        public int compareTo(Object o)
        {
            if(o == null) throw new NullPointerException();
            Node t = (Node)o;
            int relation = id.compareTo(t.id);
            return relation;
        }

        // toString produces a simple string representation
        public String toString()
        {
            StringBuilder line = new StringBuilder();
            line.append("{");
            line.append(id);
            line.append("|");
            line.append(name);
            line.append("|");
            line.append(parent);
            line.append("|");
            line.append(collectionType.toString());
            line.append("|");
            line.append(dataType.toString());
            line.append("|");
            line.append(dataFormatType.toString());
            line.append("|");
            for(int i=0;i<variables.size();i++) {
                if(i > 0) line.append(",");
                line.append(variables.get(i).toString());
            }
            line.append("}");
            return line.toString();
        }
    }

    //////////////////////////////////////////////////
    // Instance variables

    InvCatalog catalog = null;
    Node root = null;

    //////////////////////////////////////////////////
    // Constructor(s)

    public Database()
    {
    }

    /**
     * Constructor.
     *
     * @param catalog
     */
    public Database(InvCatalog catalog)
    {
        this.catalog = catalog;
    }

    //////////////////////////////////////////////////
    // dataset walker
 
    public void walk()
    {
       root = new Node(true);
       List<InvDataset> datasets = catalog.getDatasets();
       walkr(root,datasets);
    }

    void walkr(Node parent, List<InvDataset> datasets)  // recursive helper method
    {
       for(InvDataset dset: datasets) {
           Node node = new Node();
           fill(node,dset);
           // link to source dataset
           node.source = dset;
           // link parent/child
           node.parent = parent;
           parent.datasets.add(node);
           walkr(node, dset.getDatasets()); // prefix order
       }
    }
    
    void fill(Node node, InvDataset dataset)  // Fill a node from the dataset (except parent links)
    {
        node.id = dataset.getID();
        node.name = dataset.getName();
        node.collectionType = dataset.getCollectionType();
        node.dataType = dataset.getDataType();
        node.dataFormatType = dataset.getDataFormatType();
        node.variables = dataset.getVariables();
    }

    //////////////////////////////////////////////////
    // Output instance methods

    /**
     *  Walk dataset and create the complete html
     */
    public String getHTML()
    {
        try {
            Printer out = new Printer();
            // generate leader
            out.println("<html>");
            out.println("<body>");
            out.println("<table border=\"1\">");
            out.println("<tr><th>ID<th>Name<th>Collection<br>Type<th>Feature<br>Type<th>DataFormat<br>Type<th>Variables<th>Parent ID");
            Node parent = null;
            for(Node node: root.datasets) {
                htmlwalk(node,out);
            }
            out.println("</table>");
            out.println("</body>");
            out.println("</html>");
            out.close();
            return out.getWriter().toString();
        } catch (Exception e) {
            return "Error: "+e.toString();
        }
    }

    // toString produces a simple string representation
    void htmlwalk(Node node, Printer out)
        throws IOException
    {
        htmlrow(node,out);
        for(Node subnode: root.datasets) {
            htmlwalk(subnode,out);
        }
    }

    void htmlrow(Node node, Printer out)
            throws IOException
    {
        out.println("<tr><th>ID<th>Name<th>Collection<br>Type<th>Feature<br>Type<th>DataFormat<br>Type<th>Variables<th>Parent ID");
        out.println("<tr valign=\"top\">");
        out.indent();
        out.println("<td>"+node.id);
        out.println("<td>"+node.name);
        out.println("<td>"+node.collectionType);
        out.println("<td>"+node.dataType);
        out.println("<td>"+node.dataFormatType);
        boolean first = true;
        for(ThreddsMetadata.Variables var: node.variables) {
           if(!first) out.print(";");
           out.print(var.toString());
        }
        out.blankline();
        out.println("<td>"+(node.parent != null?node.parent.id:""));
        out.outdent();
    }

    /**
     *  Walk dataset and create the complete xml
     */
    public String getXML()
    {
        return "<xml/>"; 
    }
}
