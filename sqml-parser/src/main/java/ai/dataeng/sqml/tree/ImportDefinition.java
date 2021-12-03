/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ai.dataeng.sqml.tree;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class ImportDefinition extends Node {

  protected final NodeLocation location;
  protected final QualifiedName qualifiedName;
  private final Optional<Identifier> alias;

  public ImportDefinition(NodeLocation location,
      QualifiedName qualifiedName, Optional<Identifier> alias) {
    super(Optional.ofNullable(location));
    this.location = location;
    this.qualifiedName = qualifiedName;
    this.alias = alias;
  }

  public Optional<Identifier> getAlias() {
    return alias;
  }

  public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
    return visitor.visitImportDefinition(this, context);
  }

  public List<? extends Node> getChildren() {
    return null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ImportDefinition anImport = (ImportDefinition) o;
    return Objects.equals(location, anImport.location) && Objects
        .equals(qualifiedName, anImport.qualifiedName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(location, qualifiedName);
  }

  @Override
  public String toString() {
    return "Import{" +
        "location=" + location +
        ", qualifiedName=" + qualifiedName +
        '}';
  }

  public QualifiedName getQualifiedName() {
    return qualifiedName;
  }
}