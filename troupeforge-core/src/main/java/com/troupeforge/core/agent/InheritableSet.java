package com.troupeforge.core.agent;

import java.util.List;

public record InheritableSet<T>(InheritanceAction action, List<T> values) {
}
