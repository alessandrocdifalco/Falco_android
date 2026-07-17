# FALCO Desktop

Applicazione Windows nativa di catalogazione musicale, indipendente da Lovable e Supabase.

## Prima versione

- scansione ricorsiva di cartelle Windows;
- scansione WebDAV rigorosamente in sola lettura (`PROPFIND` e `GET`);
- catalogo SQLite locale in `%USERPROFILE%/.falco/falco.db`;
- metadati audio locali, ricerca immediata e stati Tieni/Dopo/Scarta;
- riproduzione locale e cache privata dei brani WebDAV;
- installer `.exe` e `.msi` prodotti da GitHub Actions.

La cache e il database sono locali. FALCO Desktop non esegue `PUT`, `DELETE`, `MOVE`, `COPY`, `MKCOL` o altre operazioni di scrittura WebDAV.
