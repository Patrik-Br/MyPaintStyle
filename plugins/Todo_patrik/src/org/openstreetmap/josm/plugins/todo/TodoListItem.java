package org.openstreetmap.josm.plugins.todo;

import org.openstreetmap.josm.data.osm.DefaultNameFormatter;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;

public class TodoListItem {
   public final IPrimitive primitive;
   public final AbstractOsmDataLayer layer;

   public TodoListItem(IPrimitive var1, AbstractOsmDataLayer var2) {
      this.primitive = var1;
      this.layer = var2;
   }

   @Override
   public boolean equals(Object var1) {
      if (this == var1) {
         return true;
      } else if (!(var1 instanceof TodoListItem var2)) {
         return false;
      } else if (this.primitive == null || var2.primitive == null) {
         return false;
      } else {
         return this.layer != null && var2.layer != null ? this.primitive.equals(var2.primitive) && this.layer.equals(var2.layer) : false;
      }
   }

   @Override
   public int hashCode() {
      return this.primitive != null && this.layer != null ? this.primitive.hashCode() ^ this.layer.hashCode() : 0;
   }

   @Override
   public String toString() {
      return this.primitive == null ? "" : this.primitive.getDisplayName(DefaultNameFormatter.getInstance());
   }
}
