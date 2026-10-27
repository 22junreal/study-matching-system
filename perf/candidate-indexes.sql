-- Optimize the measured equality-filter + newest-first page and default newest-first page.
CREATE INDEX idx_studies_filter_newest ON studies(category, level, status, created_at DESC, id DESC);
CREATE INDEX idx_studies_newest ON studies(created_at DESC, id DESC);
