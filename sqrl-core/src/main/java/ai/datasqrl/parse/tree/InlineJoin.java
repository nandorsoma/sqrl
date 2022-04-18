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
package ai.datasqrl.parse.tree;

import ai.datasqrl.parse.tree.name.Name;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class InlineJoin
    extends Declaration {

  private final Relation relation;

  private final Optional<Name> inverse;
  private final Optional<OrderBy> orderBy;
  private final Optional<Limit> limit;

  public InlineJoin(Optional<NodeLocation> location, Relation relation, Optional<OrderBy> orderBy,
      Optional<Limit> limit, Optional<Name> inverse) {
    super(location);
    this.relation = relation;
    this.inverse = inverse;
    this.orderBy = orderBy;
    this.limit = limit;
  }

  public Relation getRelation() {
    return relation;
  }

  public Optional<Name> getInverse() {
    return inverse;
  }

  public Optional<OrderBy> getOrderBy() {
    return orderBy;
  }

  public Optional<Limit> getLimit() {
    return limit;
  }

  @Override
  public <R, C> R accept(AstVisitor<R, C> visitor, C context) {
    return visitor.visitInlineJoin(this, context);
  }

  @Override
  public List<Node> getChildren() {

    return ImmutableList.of();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    InlineJoin that = (InlineJoin) o;
    return Objects.equals(relation, that.relation)
        && Objects.equals(inverse, that.inverse) && Objects.equals(orderBy,
        that.orderBy) && Objects.equals(limit, that.limit);
  }

  @Override
  public int hashCode() {
    return Objects.hash(relation, inverse, orderBy, limit);
  }
}