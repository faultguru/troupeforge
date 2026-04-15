package com.troupeforge.core.bucket;

import java.util.Map;

public record BucketConfigDescriptor(String sourceType, Map<String, String> properties) {}
