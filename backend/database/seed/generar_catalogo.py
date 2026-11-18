"""Genera app/.../data/local/CatalogoRecorridos.kt desde rutas.csv y ruta_calles.csv.
Uso (desde la raíz del proyecto): python3 backend/database/seed/generar_catalogo.py
"""
import csv, collections, os
raiz = os.path.dirname(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))))
seed = os.path.join(raiz, "backend", "database", "seed")
out = os.path.join(raiz, "app", "src", "main", "java", "cl", "conatacoch", "app", "data", "local", "CatalogoRecorridos.kt")
rutas=list(csv.DictReader(open(seed+'/rutas.csv',encoding='utf-8')))
calles=collections.defaultdict(lambda: collections.defaultdict(list))
for r in csv.DictReader(open(seed+'/ruta_calles.csv',encoding='utf-8')):
    calles[r['folio']][r['sentido']].append((int(r['orden']),r['calle'],r['comuna']))
q=lambda s:'"'+s.replace('\\','\\\\').replace('"','\\"')+'"'
L=["package cl.conatacoch.app.data.local","",
"import cl.conatacoch.app.domain.model.Calle","import cl.conatacoch.app.domain.model.Recorrido","",
"// Generado desde backend/database/seed/rutas.csv y ruta_calles.csv (recorridos oficiales).",
"// Si cambian los CSV, regenerar este archivo. Más adelante vendrá de GET /api/rutas.","",
"object CatalogoRecorridos {","    val todos: List<Recorrido> = listOf("]
for r in rutas:
    f=r['folio']
    L.append("        Recorrido(")
    L.append(f"            folio = {q(f)}, linea = {q(r['linea'])}, operador = {q(r['operador'])},")
    L.append(f"            origen = {q(r['origen_comuna'])}, destino = {q(r['destino_comuna'])},")
    for s,name in (('IDA','ida'),('REGRESO','regreso')):
        L.append(f"            {name} = listOf(")
        for _,c,co in sorted(calles[f][s]):
            L.append(f"                Calle({q(c)}, {q(co)}),")
        L.append("            ),")
    L.append("        ),")
L+=["    )","}",""]
open(out,'w',encoding='utf-8').write("\n".join(L))
print(f"{len(rutas)} recorridos -> {out}")
