// Builds a simple PDF copy of a job posting from the fields we already captured
// (title, company, URL, ids, dates, description snapshot). This is a text summary —
// not a pixel capture of the portal page — which keeps it consistent with the app's
// "we only store visible metadata" principle. Returns a Blob (application/pdf).

import { jsPDF } from 'jspdf';

function formatDate(value) {
  if (!value) return '—';
  const d = new Date(value);
  return Number.isNaN(d.getTime()) ? String(value) : d.toLocaleDateString();
}

export function buildPostingPdfBlob(tile) {
  const doc = new jsPDF({ unit: 'pt', format: 'a4' });
  const margin = 48;
  const width = doc.internal.pageSize.getWidth();
  const maxWidth = width - margin * 2;
  let y = margin;

  const line = (text, { size = 11, bold = false, gap = 16, color = 40 } = {}) => {
    doc.setFont('helvetica', bold ? 'bold' : 'normal');
    doc.setFontSize(size);
    doc.setTextColor(color);
    const wrapped = doc.splitTextToSize(String(text ?? '—'), maxWidth);
    for (const row of wrapped) {
      if (y > doc.internal.pageSize.getHeight() - margin) {
        doc.addPage();
        y = margin;
      }
      doc.text(row, margin, y);
      y += gap;
    }
  };

  line(tile.jobTitle || 'Untitled role', { size: 20, bold: true, gap: 26 });
  line(tile.company || 'Unknown company', { size: 13, gap: 22, color: 90 });

  y += 6;
  line('Portal', { size: 9, bold: true, gap: 13, color: 120 });
  line(tile.portalName || '—', { gap: 18 });

  line('Posting URL', { size: 9, bold: true, gap: 13, color: 120 });
  line(tile.jobUrl || '—', { gap: 18 });

  line('Portal job id', { size: 9, bold: true, gap: 13, color: 120 });
  line(tile.externalJobId || '—', { gap: 18 });

  line('Date applied', { size: 9, bold: true, gap: 13, color: 120 });
  line(formatDate(tile.dateApplied), { gap: 22 });

  if (tile.descriptionSnapshot) {
    line('Description snapshot', { size: 9, bold: true, gap: 13, color: 120 });
    line(tile.descriptionSnapshot, { gap: 15 });
  }

  y += 10;
  line(`Saved from Job Application Tracker on ${new Date().toLocaleString()}`, {
    size: 8,
    gap: 12,
    color: 150,
  });

  return doc.output('blob');
}

export function pdfFileName(tile) {
  const base = `${tile.company || 'company'}-${tile.jobTitle || 'posting'}`
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60);
  return `${base || 'job-posting'}.pdf`;
}
