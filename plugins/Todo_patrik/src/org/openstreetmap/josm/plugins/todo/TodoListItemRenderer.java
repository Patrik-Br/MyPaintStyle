package org.openstreetmap.josm.plugins.todo;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import javax.swing.DefaultListCellRenderer;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.ListCellRenderer;
import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.tools.ImageProvider;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.ImageProvider.ImageSizes;

public class TodoListItemRenderer implements ListCellRenderer<TodoListItem> {
   private final DefaultListCellRenderer defaultRenderer = new DefaultListCellRenderer();
   private final DefaultNameFormatter formatter = DefaultNameFormatter.getInstance();

   public Component getListCellRendererComponent(JList<? extends TodoListItem> var1, TodoListItem var2, int var3, boolean var4, boolean var5) {
      Component var6 = this.defaultRenderer.getListCellRendererComponent(var1, var2, var3, var4, var5);
      if (var6 instanceof JLabel && var2 != null && var2.primitive != null) {
         JLabel var7 = (JLabel)var6;
         IPrimitive var8 = var2.primitive;
         String var9 = var2.layer != null ? var2.layer.getName() : "";
         var7.setText(var8.getDisplayName(this.formatter) + " [" + var9 + "]");
         var7.setToolTipText(var8.getDisplayName(this.formatter));
         if (var8 instanceof OsmPrimitive) {
            Dimension var10 = ImageSizes.SMALLICON.getImageDimension();
            ImageIcon var11 = null;

            try {
               var11 = ImageProvider.getPadded((OsmPrimitive)var8, var10);
            } catch (Exception var14) {
               Logging.warn("todo_patrik: getPadded failed: " + var14.getMessage());
            }

            if (var11 == null) {
               try {
                  var11 = ImageProvider.get(var8.getType());
               } catch (Exception var13) {
                  Logging.warn("todo_patrik: get(type) failed for " + var8.getDisplayType());
               }
            }

            var7.setIcon(var11);
         }

         TodoListModel var15 = (TodoListModel)var1.getModel();
         if (var15.isDone(var2) && !var4) {
            var7.setForeground(Color.GRAY);
         }

         return var7;
      } else {
         return var6;
      }
   }
}
