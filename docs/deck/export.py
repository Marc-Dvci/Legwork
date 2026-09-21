"""Export the deck to PDF and per-slide PNGs with PowerPoint (COM). Usage: python export.py"""
import os, sys, pathlib
import win32com.client
here = pathlib.Path(__file__).resolve().parent
src = str(here / "Legwork-pitch.pptx")
out = here / "render"; out.mkdir(exist_ok=True)
for f in out.glob("*.png"): f.unlink()
app = win32com.client.Dispatch("PowerPoint.Application")
pres = app.Presentations.Open(src, WithWindow=False)
pres.SaveAs(str(here / "Legwork-pitch.pdf"), 32)  # ppSaveAsPDF
for i, slide in enumerate(pres.Slides, 1):
    slide.Export(str(out / f"slide-{i:02d}.png"), "PNG", 1920, 1080)
pres.Close(); app.Quit()
print("exported", len(list(out.glob("*.png"))), "slides")
