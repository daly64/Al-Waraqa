"""Fabrique le PDF d'exemple (guide d'Al Waraqa) utilisé pour les captures d'écran automatiques.

Usage : python3 tools/make_guide.py .github/captures/Guide-Al-Waraqa.pdf
"""
import sys
from reportlab.lib.colors import HexColor, white
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle
from reportlab.platypus import Paragraph
from reportlab.pdfgen import canvas

BRICK = HexColor("#A63A24")
INK = HexColor("#2A1D17")
SOFT = HexColor("#6B5547")
W, H = A4
LEFT, RIGHT, TOP = 72, W - 72, H - 80

body = ParagraphStyle("body", fontName="Times-Roman", fontSize=12.5, leading=18, textColor=INK, spaceAfter=10)

CHAPTERS = [
    ("Bienvenue", [
        "Al Waraqa est un lecteur de documents PDF pensé pour la lecture longue : romans, cours, "
        "contrats ou rapports. Chaque page occupe tout l'écran et se tourne comme une vraie feuille de papier.",
        "Ce guide sert aussi de document d'essai. Il contient un sommaire, des titres, des paragraphes "
        "et une illustration en couleurs, pour vérifier que chaque page s'affiche correctement.",
        "Les marges blanches sont rognées automatiquement : le texte apparaît aussi grand que possible, "
        "sans que vous ayez besoin de zoomer à chaque page.",
    ]),
    ("Tourner les pages", [
        "Posez le doigt sur la moitié droite de la page et faites-le glisser vers la gauche : la feuille se soulève, "
        "se courbe et laisse apparaître la page suivante. Relâchez avant la moitié de l'écran et la page revient à plat.",
        "Pour revenir en arrière, partez de la moitié gauche et glissez vers la droite. Un simple appui sur le bord "
        "droit ou gauche de l'écran tourne aussi la page, instantanément.",
        "Les touches de volume tournent également les pages : volume bas pour la page suivante, volume haut pour "
        "la page précédente. Ce réglage peut être désactivé.",
    ]),
    ("Zoomer", [
        "Écartez deux doigts pour agrandir la page, pincez-les pour la réduire. Une fois la page agrandie, "
        "un seul doigt suffit pour la déplacer. Après un court instant, la page est redessinée plus finement "
        "pour que le texte reste net.",
        "Un double appui au centre de la page agrandit directement l'endroit touché. Un second double appui "
        "ramène la page entière.",
    ]),
    ("Thèmes de lecture", [
        "Trois thèmes sont proposés : Clair, Sépia et Nuit. Le thème Sépia réchauffe le papier pour une lecture "
        "plus douce en journée.",
        "Le thème Nuit inverse le texte et le fond pour lire dans le noir sans être ébloui. Les photos et les "
        "illustrations, elles, gardent leurs vraies couleurs : elles sont seulement adoucies.",
    ]),
    ("Marque-pages et sommaire", [
        "Touchez le centre de la page pour afficher les barres, puis le signet pour marquer la page. Toutes les "
        "pages marquées se retrouvent dans l'onglet Marque-pages, classées par document.",
        "Le menu propose aussi le sommaire du document, quand il en a un, et la commande « Aller à la page » "
        "pour sauter directement à une page précise.",
    ]),
    ("Rechercher", [
        "La loupe permet de chercher un mot ou une phrase dans tout le document. Chaque résultat indique sa page "
        "et un extrait du texte. Touchez un résultat pour ouvrir la page : le mot y est surligné.",
        "Un bandeau permet ensuite de passer d'une page trouvée à la suivante, sans relancer la recherche. "
        "La recherche ne fonctionne pas sur les documents scannés, qui ne contiennent que des images.",
    ]),
    ("Réglages", [
        "Les réglages regroupent le thème, le mode de lecture (Livre ou Défilement), la taille de texte homogène, "
        "le son et la vibration de la page tournée, les touches de volume et l'écran toujours allumé.",
        "Toutes vos données restent sur le téléphone : documents récents, page de reprise, marque-pages et réglages.",
    ]),
]


def illustration(c, x, y, w, h):
    """Un paysage au coucher du soleil, en dégradés : une vraie image en couleurs."""
    c.saveState()
    p = c.beginPath()
    p.roundRect(x, y, w, h, 14)
    c.clipPath(p, stroke=0, fill=0)
    c.linearGradient(x, y + h, x, y, (HexColor("#2E3F7F"), HexColor("#C2617A"), HexColor("#F4A259")), extend=True)
    c.setFillColor(HexColor("#FFE3A3"))
    c.circle(x + w * 0.68, y + h * 0.42, h * 0.16, stroke=0, fill=1)
    for i, (col, base, amp) in enumerate(((HexColor("#7A3E48"), 0.30, 0.10), (HexColor("#4E2A3A"), 0.18, 0.08),
                                          (HexColor("#2B1B2B"), 0.06, 0.06))):
        hill = c.beginPath()
        hill.moveTo(x, y)
        steps = 40
        for s in range(steps + 1):
            t = s / steps
            import math
            yy = y + h * (base + amp * math.sin(t * math.pi * (2 + i) + i))
            hill.lineTo(x + w * t, yy)
        hill.lineTo(x + w, y)
        hill.close()
        c.setFillColor(col)
        c.drawPath(hill, stroke=0, fill=1)
    c.restoreState()


def main(out):
    c = canvas.Canvas(out, pagesize=A4)
    c.setTitle("Guide d'Al Waraqa")
    c.setAuthor("Al Waraqa")
    for n, (title, paras) in enumerate(CHAPTERS):
        key = f"ch{n}"
        c.bookmarkPage(key)
        c.addOutlineEntry(title, key, level=0)
        y = TOP
        if n == 0:
            c.setFont("Helvetica-Bold", 30)
            c.setFillColor(BRICK)
            c.drawString(LEFT, y - 10, "Guide d'Al Waraqa")
            c.setFont("Helvetica", 13)
            c.setFillColor(SOFT)
            c.drawString(LEFT, y - 34, "Lire ses PDF comme un livre")
            illustration(c, LEFT, y - 300, RIGHT - LEFT, 240)
            y -= 340
        c.setFont("Helvetica-Bold", 20)
        c.setFillColor(INK)
        c.drawString(LEFT, y - 6, f"{n + 1}. {title}")
        y -= 34
        for text in paras:
            para = Paragraph(text, body)
            _, ph = para.wrap(RIGHT - LEFT, y)
            para.drawOn(c, LEFT, y - ph)
            y -= ph + 12
        c.setFont("Helvetica", 10)
        c.setFillColor(SOFT)
        c.drawCentredString(W / 2, 40, f"page {n + 1}")
        c.showPage()
    c.showOutline()
    c.save()


if __name__ == "__main__":
    main(sys.argv[1])
