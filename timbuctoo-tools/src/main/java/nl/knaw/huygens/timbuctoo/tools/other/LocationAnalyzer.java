package nl.knaw.huygens.timbuctoo.tools.other;

/*
 * #%L
 * Timbuctoo tools
 * =======
 * Copyright (C) 2012 - 2014 Huygens ING
 * =======
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as
 * published by the Free Software Foundation, either version 3 of the 
 * License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public 
 * License along with this program.  If not, see
 * <http://www.gnu.org/licenses/gpl-3.0.html>.
 * #L%
 */

import nl.knaw.huygens.timbuctoo.Repository;
import nl.knaw.huygens.timbuctoo.model.Location;
import nl.knaw.huygens.timbuctoo.storage.StorageIterator;
import nl.knaw.huygens.timbuctoo.tools.config.ToolsInjectionModule;

public class LocationAnalyzer {

  public static void main(String[] args) throws Exception {
    final Repository repository = ToolsInjectionModule.createRepositoryInstance().getRepository();
    try {
      StorageIterator<Location> iterator = repository.getDomainEntities(Location.class);
      while (iterator.hasNext()) {
        Location location = iterator.next();
        System.out.println(location.getDisplayName());
      }
      iterator.close();
    } finally {
      if (repository != null) {
        repository.close();
      }
      // TODO close index manager, even if it's not used...
    }
  }

}