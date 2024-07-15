package ai.vespa.schemals.index;

/**
 * SymbolInheritanceNode
 * Its purpose is to provide a weaker check on equality and hashcode to consider two nodes equal when they reference
 * the same underlying symbol in the CST.
 */
public class SymbolInheritanceNode {

    private Symbol structDefinitionSymbol;

    public SymbolInheritanceNode(Symbol structDefinitionSymbol) {
        this.structDefinitionSymbol = structDefinitionSymbol;
    }

	@Override
    public int hashCode() {
        return (structDefinitionSymbol.getFileURI() + ":" + structDefinitionSymbol.getLongIdentifier()).hashCode();
	}

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SymbolInheritanceNode)) return false;
        return this.hashCode() == other.hashCode();
    }

    @Override
    public String toString() {
        return structDefinitionSymbol.getLongIdentifier();
    }

    public Symbol getSymbol() {
        return structDefinitionSymbol;
    }
}
