package com.troupeforge.engine.model;

import com.troupeforge.core.id.TierId;

public interface ComplexityAnalyzer {
    TierId analyze(ComplexityContext context);
}
