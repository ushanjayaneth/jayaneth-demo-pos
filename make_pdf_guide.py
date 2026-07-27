import os
import sys
from reportlab.lib import colors
from reportlab.lib.pagesizes import letter, A4
from reportlab.lib.units import inch
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.platypus import SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle, HRFlowable, KeepTogether

def build_pdf():
    desktop_dir = r"C:\Users\User\OneDrive\Desktop"
    if not os.path.exists(desktop_dir):
        desktop_dir = os.path.expanduser("~/Desktop")
    
    pdf_filename = os.path.join(desktop_dir, "Jayaneth_Demo_POS_User_Guide.pdf")
    
    doc = SimpleDocTemplate(
        pdf_filename,
        pagesize=A4,
        rightMargin=36,
        leftMargin=36,
        topMargin=36,
        bottomMargin=36
    )

    styles = getSampleStyleSheet()

    # Custom styles
    title_style = ParagraphStyle(
        'DocTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=24,
        textColor=colors.HexColor('#00d4ff'),
        spaceAfter=6,
        alignment=0
    )

    subtitle_style = ParagraphStyle(
        'DocSubTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=12,
        textColor=colors.HexColor('#ffffff'),
        spaceAfter=4
    )

    badge_style = ParagraphStyle(
        'Badge',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9,
        textColor=colors.HexColor('#cbd5e1')
    )

    h1_style = ParagraphStyle(
        'Heading1_Custom',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=14,
        textColor=colors.HexColor('#0f172a'),
        spaceBefore=14,
        spaceAfter=8
    )

    body_bold = ParagraphStyle(
        'BodyBold',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=10,
        textColor=colors.HexColor('#0f172a'),
        spaceAfter=2
    )

    body_text = ParagraphStyle(
        'BodyTextCustom',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9.5,
        textColor=colors.HexColor('#334155'),
        leading=13,
        spaceAfter=6
    )

    table_header = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9.5,
        textColor=colors.white
    )

    table_cell = ParagraphStyle(
        'TableCell',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=9,
        textColor=colors.HexColor('#1e293b'),
        leading=12
    )

    story = []

    # ──── HEADER BANNER ───────────────────────────────────────────────────────
    header_data = [
        [
            Paragraph("JAYANETH DEMO POS", title_style)
        ],
        [
            Paragraph("Smart Retail & Wholesale Management System — Complete User Guide & Feature Manual", subtitle_style)
        ],
        [
            Paragraph("Comprehensive Overview · Retail & Wholesale Billing · Net Profit Tracking · Bluetooth & USB Printers", badge_style)
        ]
    ]

    header_table = Table(header_data, colWidths=[520])
    header_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#0f172a')),
        ('PADDING', (0,0), (-1,-1), 12),
        ('LINEBELOW', (0,-1), (-1,-1), 3, colors.HexColor('#00d4ff')),
        ('BOTTOMPADDING', (0,-1), (-1,-1), 12),
    ]))

    story.append(header_table)
    story.append(Spacer(1, 14))

    # ──── SECTION 1: KEY FEATURES ─────────────────────────────────────────────
    story.append(Paragraph("1. KEY SYSTEM FEATURES & HIGHLIGHTS", h1_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#00d4ff'), spaceBefore=2, spaceAfter=10))

    features_data = [
        [
            Paragraph("<b>🏪 Retail & Wholesale Pricing (සිල්ලර & තොග)</b>", body_bold),
            Paragraph("<b>🖨️ Universal Thermal Printer Support</b>", body_bold)
        ],
        [
            Paragraph("Supports dual retail and wholesale prices per item. Switch mode seamlessly from POS screen.", body_text),
            Paragraph("Supports Bluetooth & USB OTG thermal printers. Auto text scaling for 58mm, 72mm, 80mm & custom paper widths.", body_text)
        ],
        [
            Paragraph("<b>💬 Instant WhatsApp Bill Sharing</b>", body_bold),
            Paragraph("<b>💰 Net Profit Tracking & Analysis</b>", body_bold)
        ],
        [
            Paragraph("Send digital receipt directly to customer's WhatsApp in one click upon sale completion or history lookup.", body_text),
            Paragraph("Track Cost Price per item to calculate exact Cost of Goods Sold (COGS), Gross Profit, Expenses, and Net Profit.", body_text)
        ],
        [
            Paragraph("<b>📄 One-Click PDF & CSV Exports</b>", body_bold),
            Paragraph("<b>🏷️ Barcode Scanner & Label Printing</b>", body_bold)
        ],
        [
            Paragraph("Export financial and sales audit reports directly to device Downloads as formatted PDF or CSV spreadsheets.", body_text),
            Paragraph("Scan products via camera or USB barcode scanner and print custom barcode labels to thermal printer.", body_text)
        ]
    ]

    feat_table = Table(features_data, colWidths=[255, 255])
    feat_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#f8fafc')),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor('#e2e8f0')),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
        ('PADDING', (0,0), (-1,-1), 8),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
    ]))

    story.append(feat_table)
    story.append(Spacer(1, 14))

    # ──── SECTION 2: MANAGEMENT MODULES ──────────────────────────────────────
    story.append(Paragraph("2. MANAGEMENT MODULES & CAPABILITIES", h1_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#00d4ff'), spaceBefore=2, spaceAfter=8))

    modules_table_data = [
        [Paragraph("Module", table_header), Paragraph("Description & Capability", table_header)],
        [Paragraph("<b>🔧 Repairs Management</b>", table_cell), Paragraph("Track phone/device repair jobs, store customer info, issue notes, and update statuses (Received, In Progress, Ready, Delivered).", table_cell)],
        [Paragraph("<b>🔄 Returns Management</b>", table_cell), Paragraph("Process customer sales returns, select items & qty, calculate refund amount, and restock inventory automatically.", table_cell)],
        [Paragraph("<b>👥 Customer Credit / Loans</b>", table_cell), Paragraph("Issue credit/loan bills to customers, track total due balances, and record partial/full repayments.", table_cell)],
        [Paragraph("<b>🌅 Day End & Expenses</b>", table_cell), Paragraph("Record operating expenses, compare daily income vs expenses, and print comprehensive Day End thermal reports.", table_cell)]
    ]

    mod_table = Table(modules_table_data, colWidths=[150, 360])
    mod_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#0f172a')),
        ('GRID', (0,0), (-1,-1), 0.5, colors.HexColor('#cbd5e1')),
        ('PADDING', (0,0), (-1,-1), 6),
        ('VALIGN', (0,0), (-1,-1), 'MIDDLE'),
        ('ROWBACKGROUNDS', (0,1), (-1,-1), [colors.white, colors.HexColor('#f1f5f9')])
    ]))

    story.append(mod_table)
    story.append(Spacer(1, 14))

    # ──── SECTION 3: STEP-BY-STEP USER GUIDE ──────────────────────────────────
    story.append(Paragraph("3. STEP-BY-STEP USER MANUAL", h1_style))
    story.append(HRFlowable(width="100%", thickness=1.5, color=colors.HexColor('#00d4ff'), spaceBefore=2, spaceAfter=8))

    guide_steps = [
        ("Step 1: Billing & POS Mode Switching", "Select <b>🏪 Retail</b> or <b>📦 Wholesale</b> at top of POS screen. Add items by clicking card or scanning barcode. Choose payment method (Cash/Card/Loan) and finalize. Print or share via WhatsApp."),
        ("Step 2: Thermal Printer Setup", "Navigate to <b>Settings → Printer Setup</b>. Connect USB (via OTG) or paired Bluetooth printer. Select paper size (58mm, 72mm, 80mm, or Custom mm) to scale receipt layout automatically."),
        ("Step 3: Inventory & Cost Price Entry", "Go to <b>Products</b> screen and click <b>+ Add Product</b>. Enter Product Name, Retail Price, Wholesale Price, and <b>Cost Price</b> to enable net profit calculations."),
        ("Step 4: Net Profit Reports & PDF/CSV Export", "Go to <b>Reports</b> screen and filter by Today, This Week, or This Month. Review Net Profit Card (Sales - COGS - Expenses). Click <b>Export PDF</b> or <b>Export CSV</b> to save reports."),
        ("Step 5: Day End & Operating Expenses", "Go to <b>Day End</b> screen to log daily business expenses. Click <b>Print Day End Report</b> to print total revenue, expenses, and net profit to thermal printer.")
    ]

    step_rows = []
    for title, desc in guide_steps:
        step_rows.append([
            Paragraph(f"<b>{title}</b><br/>{desc}", table_cell)
        ])

    step_table = Table(step_rows, colWidths=[510])
    step_table.setStyle(TableStyle([
        ('BACKGROUND', (0,0), (-1,-1), colors.HexColor('#f0fdf4')),
        ('BOX', (0,0), (-1,-1), 1, colors.HexColor('#22c55e')),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.HexColor('#bbf7d0')),
        ('PADDING', (0,0), (-1,-1), 8),
    ]))

    story.append(step_table)
    story.append(Spacer(1, 16))

    # Footer note
    footer_text = Paragraph("<font color='#64748b' size=8>Jayaneth Demo POS System Guide · Built for Android & Thermal Printers · Exported to Desktop</font>", ParagraphStyle('Footer', alignment=1))
    story.append(footer_text)

    doc.build(story)

    # Copy to E:\MY POS if directory exists
    alt_dir = r"E:\MY POS"
    if os.path.exists(alt_dir):
        alt_file = os.path.join(alt_dir, "Jayaneth_Demo_POS_User_Guide.pdf")
        with open(pdf_filename, 'rb') as src, open(alt_file, 'wb') as dst:
            dst.write(src.read())

    print(f"SUCCESS: PDF guide generated at {pdf_filename}")

if __name__ == '__main__':
    build_pdf()
