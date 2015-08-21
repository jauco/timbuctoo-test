package nl.knaw.huygens.timbuctoo.index.indexer;

import nl.knaw.huygens.timbuctoo.Repository;
import nl.knaw.huygens.timbuctoo.index.IndexException;
import nl.knaw.huygens.timbuctoo.index.IndexManager;
import nl.knaw.huygens.timbuctoo.model.DomainEntity;

class AddIndexer extends AbstractIndexer {

  public AddIndexer(Repository repository, IndexManager indexManager) {
    super(repository, indexManager);
  }

  @Override
  public void executeIndexAction(Class<? extends DomainEntity> type, String id) throws IndexException {
    getIndexManager().addEntity(type, id);
  }
}
