package nl.knaw.huygens.timbuctoo.model.vre;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static nl.knaw.huygens.timbuctoo.model.vre.VreBuilder.vre;

public class Vres {
  private final Map<String, Collection> collections = new HashMap<>();
  private final Map<String, Vre> vres;

  public Vres(List<Vre> vres) {
    this.vres = vres.stream().collect(toMap(Vre::getVreName, vre1 -> vre1));
    vres.stream()
      .flatMap(vre -> vre.getCollections().values().stream())
      .forEach(collection -> {
        if (collections.containsKey(collection.getCollectionName())) {
          throw new RuntimeException("Collection was defined multiple times: " + collection.getCollectionName());
        }
        collections.put(collection.getCollectionName(), collection);
      });
  }

  public Collection get(String collection) {
    return collections.get(collection);
  }

  public Vre getVre(String vre) {
    return vres.get(vre);
  }

  public static class Builder {
    private final List<VreBuilder> vres = new ArrayList<>();

    public Builder withVre(String name, String prefix, Consumer<VreBuilder> config) {
      VreBuilder vre = vre(name, prefix);
      vres.add(vre);
      config.accept(vre);
      return this;
    }

    public Builder withVre(String name, String prefix) {
      vres.add(vre(name, prefix));
      return this;
    }

    public Vres build() {
      return new Vres(vres.stream().map(VreBuilder::build).collect(toList()));
    }
  }
}
